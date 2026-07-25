# Database

SQLite through SQLDelight. The schema is written by hand in `.sq` files, queries are compiled to
type-safe Kotlin at build time, and migrations are explicit `.sqm` files. There is no runtime
reflection and no code generation surprise: a wrong column name is a compile error.

Location: `core/database/src/commonMain/sqldelight/app/calio/database/`

## Conventions

- Table and column names are `snake_case`; identifiers are `TEXT` UUIDs.
- Timestamps are `INTEGER` epoch milliseconds. Wall-clock values are `TEXT` in ISO-8601.
- Every synced table carries the audit columns: `created_at`, `updated_at`, `revision`,
  `deleted_at`, `origin_device`.
- Deletes are soft. `deleted_at IS NULL` is part of every read query; tombstones are pruned by a
  maintenance task once every device has acknowledged them.
- Foreign keys are enabled and declared `ON DELETE CASCADE` for owned child rows
  (reminders, attendees, attachments, overrides).

## Tables

### `calendar`

```sql
CREATE TABLE calendar (
    id             TEXT    NOT NULL PRIMARY KEY,
    name           TEXT    NOT NULL,
    color          INTEGER NOT NULL,
    is_visible     INTEGER NOT NULL DEFAULT 1,
    is_default     INTEGER NOT NULL DEFAULT 0,
    sort_order     INTEGER NOT NULL DEFAULT 0,
    created_at     INTEGER NOT NULL,
    updated_at     INTEGER NOT NULL,
    revision       TEXT    NOT NULL,
    deleted_at     INTEGER,
    origin_device  TEXT    NOT NULL
);
```

### `category`

Same audit tail; columns `name`, `color`, `is_built_in`, `sort_order`, with a unique index on
`lower(name)` where not deleted.

### `event`

The central table. Note the split between wall-clock truth and the UTC index.

```sql
CREATE TABLE event (
    id                TEXT    NOT NULL PRIMARY KEY,
    calendar_id       TEXT    NOT NULL REFERENCES calendar(id),
    category_id       TEXT             REFERENCES category(id),
    title             TEXT    NOT NULL,
    description       TEXT,
    notes             TEXT,
    location_label    TEXT,
    location_lat      REAL,
    location_lon      REAL,

    is_all_day        INTEGER NOT NULL DEFAULT 0,
    start_local       TEXT    NOT NULL,   -- ISO local date-time, or date for all-day
    end_local         TEXT    NOT NULL,   -- exclusive
    time_zone_id      TEXT,               -- NULL for all-day / floating
    start_utc         INTEGER NOT NULL,   -- derived index
    end_utc           INTEGER NOT NULL,   -- derived index

    recurrence_rule   TEXT,               -- RFC 5545 RRULE text, NULL if single
    recurrence_until_utc INTEGER,         -- upper bound for range pruning, NULL = open ended

    color_override    INTEGER,
    kind              TEXT    NOT NULL DEFAULT 'STANDARD',
    busy_status       TEXT    NOT NULL DEFAULT 'BUSY',
    status            TEXT    NOT NULL DEFAULT 'CONFIRMED',

    created_at        INTEGER NOT NULL,
    updated_at        INTEGER NOT NULL,
    revision          TEXT    NOT NULL,
    deleted_at        INTEGER,
    origin_device     TEXT    NOT NULL
);

CREATE INDEX event_range_idx      ON event(start_utc, end_utc);
CREATE INDEX event_calendar_idx   ON event(calendar_id);
CREATE INDEX event_recurring_idx  ON event(recurrence_until_utc) WHERE recurrence_rule IS NOT NULL;
```

`recurrence_until_utc` is what keeps the range query cheap: a visible window loads
(a) non-recurring events overlapping the window, and (b) recurring events whose series has not ended
before the window starts. Occurrences are then expanded in memory. Nothing is materialised into the
database, so editing a series never requires rewriting thousands of rows.

### `recurrence_override`

```sql
CREATE TABLE recurrence_override (
    event_id        TEXT NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    original_start  TEXT NOT NULL,     -- identifies the occurrence
    type            TEXT NOT NULL,     -- CANCELLED | MODIFIED
    patch_json      TEXT,              -- serialised EventPatch, NULL when cancelled
    PRIMARY KEY (event_id, original_start)
);
```

### `reminder`

```sql
CREATE TABLE reminder (
    id            TEXT    NOT NULL PRIMARY KEY,
    event_id      TEXT             REFERENCES event(id) ON DELETE CASCADE,
    task_id       TEXT             REFERENCES task(id)  ON DELETE CASCADE,
    trigger_type  TEXT    NOT NULL,  -- RELATIVE_START | RELATIVE_END | ABSOLUTE
    offset_minutes INTEGER,
    absolute_utc  INTEGER,
    channel       TEXT    NOT NULL,
    CHECK ((event_id IS NULL) <> (task_id IS NULL))
);
```

The `CHECK` constraint enforces that a reminder belongs to exactly one parent.

### `attendee`, `attachment`

Child tables of `event`, cascade-deleted, with a `sort_order` for stable display.

### `task`

```sql
CREATE TABLE task (
    id               TEXT    NOT NULL PRIMARY KEY,
    parent_task_id   TEXT             REFERENCES task(id) ON DELETE CASCADE,
    category_id      TEXT             REFERENCES category(id),
    title            TEXT    NOT NULL,
    description      TEXT,
    priority         TEXT    NOT NULL DEFAULT 'NONE',
    due_local        TEXT,
    due_time_zone_id TEXT,
    due_utc          INTEGER,
    progress_percent INTEGER NOT NULL DEFAULT 0,
    is_completed     INTEGER NOT NULL DEFAULT 0,
    completed_at     INTEGER,
    sort_order       INTEGER NOT NULL DEFAULT 0,
    /* audit tail */
);

CREATE INDEX task_due_idx    ON task(due_utc);
CREATE INDEX task_parent_idx ON task(parent_task_id);
```

### Search: `search_index`

Full-text search uses an FTS5 external-content table so the text is not duplicated.

```sql
CREATE VIRTUAL TABLE search_index USING fts5(
    title, description, notes, location,
    entity_id UNINDEXED,
    entity_type UNINDEXED,
    tokenize = 'unicode61 remove_diacritics 2'
);
```

Triggers on `event` and `task` keep the index in sync on insert, update and soft delete. Diacritic
folding means "Buro" finds "Büro". Ranking uses `bm25()` with a higher weight on `title`.

### Sync: `change_log`

The outbox. One row per local mutation, written in the same transaction as the mutation itself.

```sql
CREATE TABLE change_log (
    seq            INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    entity_type    TEXT    NOT NULL,
    entity_id      TEXT    NOT NULL,
    operation      TEXT    NOT NULL,   -- UPSERT | DELETE
    revision       TEXT    NOT NULL,   -- hybrid logical clock
    created_at     INTEGER NOT NULL,
    synced_at      INTEGER             -- NULL while pending
);

CREATE INDEX change_log_pending_idx ON change_log(synced_at) WHERE synced_at IS NULL;
```

### Sync: `sync_state`, `sync_conflict`

`sync_state` is a single-row table holding `device_id`, `remote_cursor`, `last_sync_at` and the
logical clock counter. `sync_conflict` stores entity id, both competing revisions and the resolution,
so the UI can show a conflict banner and offer the discarded version.

## Migrations

- The schema version is the number of the highest applied `.sqm` file.
- Verification of every migration runs in CI against a snapshot of the previous schema, which
  SQLDelight generates and validates.
- Migrations never drop a column that a released version wrote; deprecated columns stay nullable
  until a later cleanup migration.

## Test setup

`core/testing` provides an in-memory JDBC driver factory. Every repository test creates a fresh
database, runs the real schema and exercises the real queries. Repository tests therefore verify the
actual SQL rather than a mock, and they still run in milliseconds.
