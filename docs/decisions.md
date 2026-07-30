# Decision log

Each entry records a decision that is expensive to reverse, the alternatives that were considered and
the consequences accepted. Entries are append-only; a superseded decision gets a new entry that
references the old one.

---

## 001 — Kotlin Multiplatform with Compose Multiplatform

**Status:** accepted · 2026-07-25

**Context.** The application targets Windows and Android from one codebase, must feel native in
responsiveness, needs a custom-drawn calendar grid with drag and drop, and has to stay maintainable
and unit-testable over years.

**Decision.** Kotlin Multiplatform for all logic, Compose Multiplatform for the user interface, with
Android and JVM/Desktop targets.

**Alternatives.**

- *Flutter* — comparable rendering quality and a faster cold start on Windows, but a second language
  and ecosystem, and a state model further from classic MVVM.
- *.NET MAUI* — native MVVM and C#, but the weakest option for heavily custom UI such as a time grid
  with drag and drop, and the largest rendering divergence between Windows and Android.

**Consequences.** One language for domain, data and UI. Both targets run on the JVM, so a shared JVM
source set can be used for cryptography and file handling. The Windows build ships a bundled runtime,
which costs installer size; start-up time is mitigated by rendering the first frame from cached state.

---

## 002 — Clean Architecture with feature modules and MVVM screens

**Status:** accepted · 2026-07-25

**Context.** The feature set grows well beyond the initial scope, and business rules such as
recurrence, conflict detection and free-slot computation must be verifiable without a UI.

**Decision.** Three layers with inward-pointing dependencies, use cases as the unit of business
logic, one immutable `UiState` plus one `StateFlow` per screen, and feature modules that may depend
on `domain` but never on `data`.

**Consequences.** Slightly more boilerplate per screen. In exchange, features cannot reach storage,
so they stay testable by construction, and the module graph prevents accidental coupling instead of
relying on review discipline.

---

## 003 — Wall-clock time as truth, UTC as a derived index

**Status:** accepted · 2026-07-25

**Context.** Storing only UTC breaks recurring events across daylight-saving changes; storing only
local time makes range queries impossible in SQL.

**Decision.** Persist the wall-clock value plus its time zone as the source of truth, and maintain
`start_utc` / `end_utc` as derived columns for range queries. All-day events store a date range with
no zone.

**Consequences.** One mapper is responsible for keeping the derived values consistent, and it is
covered by tests around daylight-saving boundaries. Range queries stay simple integer comparisons.

---

## 004 — SQLDelight over an ORM

**Status:** accepted · 2026-07-25

**Context.** The schema needs full-text search, partial indices, triggers and an outbox table, and
the same schema must run on Android and on the desktop JVM.

**Decision.** Hand-written SQL compiled to type-safe Kotlin by SQLDelight, with explicit migration
files.

**Consequences.** Full control over indices, FTS5 and triggers. Queries are compile-time checked, and
repository tests run against the real schema in memory rather than against mocks.

---

## 005 — Recurrences expanded on read, never materialised

**Status:** accepted · 2026-07-25

**Context.** A daily event with no end date has unbounded occurrences. Writing them into the database
would make editing a series a mass update and would bloat synchronisation.

**Decision.** Store the rule; expand occurrences in memory for the visible range only. Exceptions are
stored as overrides keyed by the original occurrence start, holding a patch rather than a full copy.

**Consequences.** Editing a series is a single row update. Expansion performance becomes a
correctness-critical, and therefore heavily tested, pure function.

---

## 006 — Local-first synchronisation with a hybrid logical clock

**Status:** accepted · 2026-07-25

**Context.** Two devices must converge without a central authority, while both may edit offline. Wall
clocks between devices disagree.

**Decision.** Every mutation writes an outbox record in the same transaction as the entity. Conflicts
are resolved last-writer-wins ordered by a hybrid logical clock with a device tiebreaker, and every
resolved conflict is recorded so the discarded version can be restored.

**Alternatives.** Server-authoritative sync (rejected: requires a backend before the app is usable);
CRDT merge per field (rejected for now: significant complexity for a data shape where whole-entity
edits dominate — the recorded conflict rows keep this path open).

**Consequences.** Convergence is deterministic and identical on every device. Losing an edit silently
is prevented by the conflict record rather than by blocking the user.

---

## 007 — Encryption envelope from the first version

**Status:** accepted · 2026-07-25

**Context.** Retrofitting end-to-end encryption after data has been synchronised requires migrating
every stored record on every device and every backend.

**Decision.** All synchronised payloads travel inside a sealed envelope carrying ciphertext, nonce,
key id and scheme, from the very first version. Two cipher implementations exist: a pass-through for
local-only operation and AES-256-GCM with keys derived from a passphrase via Argon2id, stored in
platform secure storage.

**Consequences.** The remote never needs plaintext, so any blob store qualifies as a backend.
Enabling encryption later is a binding change, not a data migration. Server-side search and
server-side conflict merging are permanently excluded — both are client responsibilities.

---

## 008 — Focus blocks and buffers are events

**Status:** accepted · 2026-07-25

**Context.** Focus times and buffers must block slots, be visible, movable and deletable, and appear
in exports.

**Decision.** Model them as events with an `EventKind` discriminator rather than as separate entities.

**Consequences.** They inherit storage, synchronisation, export and rendering for free. Planning use
cases filter by kind, and conflict detection can exclude buffers from warnings.

---

## 009 — Provider sign-in without a client secret, tokens outside the database

**Status:** accepted · 2026-07-30

**Context.** Google and Microsoft calendars are reached with OAuth 2.0. Calio is installed on the
user's machine, so any client secret compiled into it can be read out of the binary by anyone who
has a copy, and the refresh token it obtains is a standing key to a calendar that stays valid until
somebody revokes it.

**Decision.** The authorization code flow with PKCE and no client secret. The sign-in happens in the
system browser — on the desktop the redirect is caught by a short-lived listener bound to the
loopback interface on a port the operating system assigns, on Android by a custom scheme. Tokens
never reach the database: they are kept in a `SecretStore`, backed by the Windows data protection
API on the desktop and by a non-extractable key in the Android keystore on the phone.

A refusal from the token endpoint is classified rather than reported: `invalid_grant` and its
siblings mean the grant is gone and the account has to be connected again, while a timeout or a
server error changes nothing about the account and is retried later.

**Alternatives.** A client secret shipped with the application (rejected: not a secret, and both
providers document this); an embedded web view for the sign-in (rejected: an application that renders
the password field is indistinguishable from one that reads it, and providers block it).

**Consequences.** The database file can be copied, backed up and exported without carrying access to
anybody's calendar; what it holds is the fact that an account is connected, not the means to use it.
The client ids have to be registered per provider and per platform, and they are configuration rather
than source. Losing the secure storage — a new Windows account, a restored phone — costs a new
sign-in and nothing else.
