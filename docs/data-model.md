# Data model

All entities live in `core/model` as immutable Kotlin data classes. Identifiers are inline value
classes wrapping a UUID string, so an `EventId` can never be passed where a `TaskId` is expected.

```kotlin
@JvmInline value class EventId(val value: String)
```

## Time representation

Calendar correctness depends almost entirely on how time is stored. Three cases must be
distinguished, and they are stored differently on purpose:

| Case                   | Meaning                                          | Stored as                                    |
| ---------------------- | ------------------------------------------------ | -------------------------------------------- |
| Zoned event            | "14:00 in Europe/Berlin"                         | wall-clock `LocalDateTime` + `timeZoneId`     |
| All-day event          | "3 August", independent of any zone              | `LocalDate` range, `timeZoneId = null`        |
| Range query support    | overlap tests must be a plain integer comparison | derived `startUtc` / `endUtc` epoch millis    |

The wall-clock value is the source of truth; the UTC values are a **derived index**. They are
recomputed whenever the event or its time zone changes. Storing only UTC would silently move
recurring events across daylight-saving boundaries; storing only wall time would make range queries
impossible in SQL. Both are kept, and a single mapper is responsible for keeping them consistent.

`end` is exclusive. An all-day event on 3 August has `startDate = 2026-08-03`,
`endDateExclusive = 2026-08-04`.

## Entities

### Calendar

A container that can be shown or hidden as a whole.

| Field         | Type            | Notes                                        |
| ------------- | --------------- | -------------------------------------------- |
| `id`          | `CalendarId`    |                                              |
| `name`        | `String`        | e.g. Private, Work, Family                   |
| `color`       | `CalioColor`    | ARGB value                                   |
| `isVisible`   | `Boolean`       | toggled from the sidebar                     |
| `isDefault`   | `Boolean`       | exactly one calendar is the default          |
| `sortOrder`   | `Int`           |                                              |
| `audit`       | `AuditFields`   | see below                                    |

### Category

Cross-calendar colour label (Work, Private, Family, Study, Leisure, plus user-defined).

| Field       | Type          | Notes                              |
| ----------- | ------------- | ---------------------------------- |
| `id`        | `CategoryId`  |                                    |
| `name`      | `String`      | unique, case-insensitive           |
| `color`     | `CalioColor`  |                                    |
| `isBuiltIn` | `Boolean`     | built-ins may be renamed, not deleted |
| `sortOrder` | `Int`         |                                    |

An event's effective colour resolves in order: event override → category → calendar.

### Event

| Field                | Type                     | Notes                                        |
| -------------------- | ------------------------ | -------------------------------------------- |
| `id`                 | `EventId`                |                                              |
| `calendarId`         | `CalendarId`             |                                              |
| `categoryId`         | `CategoryId?`            |                                              |
| `title`              | `String`                 |                                              |
| `description`        | `String?`                |                                              |
| `notes`              | `String?`                | kept separate from description for search and export |
| `location`           | `EventLocation?`         | label plus optional coordinates              |
| `timeRange`          | `EventTimeRange`         | sealed type: `Zoned` or `AllDay`             |
| `recurrence`         | `RecurrenceRule?`        | null for single occurrences                  |
| `reminders`          | `List<Reminder>`         | ordered, may be empty                        |
| `attendees`          | `List<Attendee>`         |                                              |
| `attachments`        | `List<Attachment>`       |                                              |
| `colorOverride`      | `CalioColor?`            |                                              |
| `kind`               | `EventKind`              | `STANDARD`, `FOCUS`, `BUFFER`                |
| `busyStatus`         | `BusyStatus`             | `BUSY`, `FREE`, `TENTATIVE` — drives conflict detection |
| `status`             | `EventStatus`            | `CONFIRMED`, `TENTATIVE`, `CANCELLED`        |
| `audit`              | `AuditFields`            |                                              |

`EventKind.FOCUS` is what makes focus blocks a first-class concept instead of a naming convention:
focus blocks are events, so they are stored, synced, exported and rendered by the same code, but the
planning use cases can treat them differently (highlighted, blocking, excluded from buffers).

### RecurrenceRule

Modelled on RFC 5545 so that ICS import/export is lossless.

| Field        | Type                    | Notes                                        |
| ------------ | ----------------------- | -------------------------------------------- |
| `frequency`  | `Frequency`             | `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`       |
| `interval`   | `Int`                   | ≥ 1; covers "every 3 weeks"                  |
| `byWeekDays` | `Set<WeekDayOccurrence>`| e.g. Mon+Wed, or "2nd Tuesday"               |
| `byMonthDay` | `Set<Int>`              | negative values count from month end         |
| `byMonth`    | `Set<Int>`              |                                              |
| `weekStart`  | `DayOfWeek`             | affects weekly expansion                     |
| `end`        | `RecurrenceEnd`         | `Never`, `OnDate(date)`, `AfterCount(n)`     |

The rule is a value object with no behaviour. Expansion lives in a `RecurrenceExpander` in the
domain layer, which turns a rule plus a date range into a list of occurrences. It is pure and
deterministic, therefore fully unit-testable, including leap years, month-end clamping and
daylight-saving transitions.

### RecurrenceOverride

Exceptions to a series. Keyed by the *original* start of the occurrence.

| Field                 | Type              | Notes                                       |
| --------------------- | ----------------- | ------------------------------------------- |
| `eventId`             | `EventId`         | the series                                  |
| `originalStart`       | `LocalDateTime`   | identifies the occurrence                   |
| `type`                | `OverrideType`    | `CANCELLED` or `MODIFIED`                   |
| `patch`               | `EventPatch?`     | only the fields that differ                 |

Storing a patch rather than a full copy means an edit to the series still propagates to fields the
user did not override.

### Reminder

Attached to an event or a task; several per parent are allowed.

| Field       | Type              | Notes                                           |
| ----------- | ----------------- | ----------------------------------------------- |
| `id`        | `ReminderId`      |                                                 |
| `trigger`   | `ReminderTrigger` | `BeforeStart(leadMinutes)`, `BeforeEnd(leadMinutes)`, `Absolute(instant)` |
| `channel`   | `ReminderChannel` | `NOTIFICATION`, `ALARM`                         |

Relative triggers carry a non-negative lead time and say in their name what they are relative to,
rather than encoding "before" as a negative number — sign conventions get inverted once and then ship
silently. 5 minutes / 30 minutes / 1 day are presets, not special cases.

### Attendee

| Field      | Type               | Notes                                        |
| ---------- | ------------------ | -------------------------------------------- |
| `name`     | `String`           |                                              |
| `email`    | `String?`          |                                              |
| `role`     | `AttendeeRole`     | `ORGANIZER`, `REQUIRED`, `OPTIONAL`          |
| `response` | `ResponseStatus`   | `NEEDS_ACTION`, `ACCEPTED`, `DECLINED`, `TENTATIVE` |

### Attachment

Metadata is synced; binary content is stored locally and referenced by checksum, so a large file
never blocks a sync round.

| Field        | Type       | Notes                              |
| ------------ | ---------- | ---------------------------------- |
| `fileName`   | `String`   |                                    |
| `mimeType`   | `String`   |                                    |
| `sizeBytes`  | `Long`     |                                    |
| `localPath`  | `String?`  | null when not yet downloaded       |
| `checksum`   | `String`   | SHA-256, also the deduplication key |

### Task

| Field           | Type            | Notes                                             |
| --------------- | --------------- | ------------------------------------------------- |
| `id`            | `TaskId`        |                                                   |
| `parentTaskId`  | `TaskId?`       | subtasks are tasks; one nesting level is enforced by a use case, not by the schema |
| `title`         | `String`        |                                                   |
| `description`   | `String?`       |                                                   |
| `priority`      | `Priority`      | `NONE`, `LOW`, `MEDIUM`, `HIGH`                   |
| `due`           | `TaskDue?`      | `Date(LocalDate)` or `DateTime(LocalDateTime, zone)` |
| `categoryId`    | `CategoryId?`   |                                                   |
| `progressPercent` | `Int`         | 0–100; derived from subtasks when they exist      |
| `isCompleted`   | `Boolean`       |                                                   |
| `completedAt`   | `Instant?`      |                                                   |
| `reminders`     | `List<Reminder>`|                                                   |
| `sortOrder`     | `Int`           | manual ordering within a parent                   |

### WorkingHours

| Field       | Type                          | Notes                                  |
| ----------- | ----------------------------- | -------------------------------------- |
| `days`      | `Map<DayOfWeek, DayWindow?>`  | null means non-working day             |
| `DayWindow` | `start: LocalTime, end: LocalTime` |                                   |

Working hours feed free-slot calculation and the dimming of non-working ranges in the day and week
grids. They are settings, not events.

### BufferPolicy

| Field                | Type      | Notes                                        |
| -------------------- | --------- | -------------------------------------------- |
| `isEnabled`          | `Boolean` |                                              |
| `defaultMinutes`     | `Int`     | buffer inserted around qualifying events     |
| `appliesToAllDay`    | `Boolean` | normally false                               |
| `minimumGapMinutes`  | `Int`     | below this gap no buffer is suggested        |

Buffers are materialised as `EventKind.BUFFER` events so they are visible, movable and deletable.

### AuditFields

Embedded in every synced entity. This is the contract the sync engine relies on.

| Field        | Type            | Notes                                                     |
| ------------ | --------------- | --------------------------------------------------------- |
| `createdAt`  | `Instant`       |                                                           |
| `updatedAt`  | `Instant`       | device wall clock, for display only                       |
| `revision`   | `HybridClock`   | logical timestamp used for conflict resolution            |
| `deletedAt`  | `Instant?`      | soft delete; the row remains as a tombstone until pruned  |
| `originDevice` | `DeviceId`    | which device produced the current revision                |

## Derived, not stored

The following are computed by use cases and never persisted, so they cannot go stale:

- **Occurrences** of a recurring event within a visible range.
- **Conflicts** between events (overlap of two `BUSY` occurrences).
- **Free slots** (working-hours window minus busy occurrences minus buffers).
- **Task progress** for a task that has subtasks.
