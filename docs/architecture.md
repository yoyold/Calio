# Architecture

## Goals

The architecture is optimised for four properties, in this order:

1. **Testability** — business rules must be verifiable without a device, a database or a UI.
2. **Replaceability** — storage, synchronisation transport and crypto must be swappable behind interfaces.
3. **Extensibility** — a new feature is a new module, not a change to existing ones.
4. **Responsiveness** — the UI reads from local storage only and never blocks on the network.

## Layers

Calio follows Clean Architecture with three concentric layers. Dependencies point **inwards only**.

```
        ┌──────────────────────────────────────────────┐
        │  Presentation      feature/*  core/ui         │
        │  Compose UI + ViewModel (MVVM)                │
        └───────────────────┬──────────────────────────┘
                            │ depends on
        ┌───────────────────▼──────────────────────────┐
        │  Domain            domain/                   │
        │  Entities · Repository contracts · Use cases │
        │  Pure Kotlin, no framework dependencies      │
        └───────────────────▲──────────────────────────┘
                            │ implements
        ┌───────────────────┴──────────────────────────┐
        │  Data              data/  core/database …    │
        │  Repositories · Mappers · Sync · Crypto      │
        └──────────────────────────────────────────────┘
```

### Domain layer (`domain/`)

Plain Kotlin. No Compose, no SQLDelight, no Android, no coroutine dispatcher assumptions.

- **Entities** live in `core/model` so that both `domain` and `data` can reference them without
  a cycle. They are immutable data classes with value-object identifiers.
- **Repository contracts** are declared here (`EventRepository`, `TaskRepository`, …) and expose
  `Flow<T>` for reads and `suspend fun` for writes.
- **Use cases** are single-purpose classes with one `operator fun invoke(...)`. They hold the rules
  that must never leak into the UI: recurrence expansion, conflict detection, free-slot computation,
  buffer insertion, progress roll-up.

A use case is the unit that gets a focused unit test. This is where the majority of test value sits.

### Data layer (`data/`, `core/database`, `core/sync`, `core/crypto`, `core/preferences`)

Implements the domain contracts.

- Repositories translate between **database rows** and **domain entities** through explicit mappers.
  The domain never sees a generated SQLDelight type.
- Every mutating repository call writes the entity **and** an outbox record in a single transaction.
  This is what makes synchronisation reliable without a background daemon holding state.
- `core/sync` owns the sync engine and depends on a `RemoteSyncSource` interface. Transport is a
  detail; the engine does not know whether it talks to a local folder, WebDAV or a cloud service.
- `core/crypto` owns key derivation, key storage and payload sealing. All sync payloads pass through
  it, even while the local-only provider is in use, so no format migration is needed later.

### Presentation layer (`feature/*`, `core/ui`, `core/designsystem`)

One MVVM screen = one `ViewModel` + one immutable `UiState` + one `UiEvent` sealed interface.

```kotlin
class MonthViewModel(
    observeMonth: ObserveMonthUseCase,
) : ViewModel() {
    val state: StateFlow<MonthUiState>
    fun onEvent(event: MonthUiEvent)
}
```

Rules:

- The `ViewModel` exposes exactly one `StateFlow` of an immutable state object.
- Composables are pure functions of that state; they never call repositories.
- Every composable that renders state has a stateless variant taking `state` and `onEvent`, which is
  what previews and screenshot tests use.
- `ViewModel`s depend on **use cases**, never on repositories directly, so a screen can be tested
  against fake use cases.

## Module graph

```
apps/android ─┐
              ├─► shared ──► feature/* ──► domain ──► core/model
apps/desktop ─┘      │            │           ▲
                     │            └──► core/designsystem, core/ui
                     └──► data ──────────┘
                            └──► core/database, core/sync, core/crypto,
                                 core/preferences, core/notifications, core/export
```

| Module                | Responsibility                                                         |
| --------------------- | ---------------------------------------------------------------------- |
| `apps/android`        | Android `Application`, activity, notification channels, work scheduling |
| `apps/desktop`        | JVM `main`, window management, tray, packaging                          |
| `shared`              | Composition root: builds the object graph and hosts the root composable |
| `core/model`          | Domain entities, identifiers, enums — zero dependencies                |
| `core/common`         | `Result` wrappers, dispatcher provider, logging, id generation          |
| `core/datetime`       | Time zone handling, week/month arithmetic, formatting contracts         |
| `core/database`       | SQLDelight schema, drivers, transaction helper                          |
| `core/preferences`    | Typed key–value settings storage                                       |
| `core/crypto`         | Key derivation, key storage, payload sealing                           |
| `core/sync`           | Sync engine, outbox draining, conflict resolution, scheduling contracts |
| `core/notifications`  | Reminder scheduling and delivery per platform                          |
| `core/export`         | ICS, CSV and PDF writers, backup archive                               |
| `core/designsystem`   | Colour, typography, spacing, motion tokens and atoms                    |
| `core/ui`             | Reusable composables and window-size utilities                          |
| `core/testing`        | Fixtures, fakes, in-memory drivers, coroutine test helpers              |
| `domain`              | Repository contracts and use cases                                     |
| `data`                | Repository implementations and mappers                                 |
| `feature/*`           | One vertical slice of user-facing functionality                        |

### Enforced dependency rules

- `core/model` depends on nothing.
- `domain` depends on `core/model` and `core/common` only.
- `feature/*` may depend on `domain`, `core/designsystem`, `core/ui` — **never** on `data`.
- `feature/*` modules never depend on each other. Shared UI moves down into `core/ui`.
- Only `shared` may see both `data` and `feature/*`; it is the single place where implementations
  are bound to contracts.

Because feature modules cannot reach the data layer, a screen can only be built against interfaces,
which keeps it unit-testable by construction.

## Platform boundaries

Android and Desktop are both JVM targets, so platform-specific code is kept small and expressed with
`expect`/`actual` declarations in three places only:

- database driver creation,
- secure key storage (Android Keystore vs. Windows credential storage),
- reminder scheduling and notification display.

Everything else lives in `commonMain`. A shared JVM source set is used where the JDK API is
sufficient for both targets, for example symmetric encryption.

## Threading

- Repositories and use cases are `suspend`/`Flow` based and never assume a dispatcher.
- A `DispatcherProvider` from `core/common` is injected, so tests substitute a test dispatcher.
- Database reads are exposed as `Flow` and observe SQLite change notifications, so the UI updates
  without manual refresh calls.

## Extension points

Adding a feature means: add a module under `feature/`, add use cases under `domain/`, register the
route in the navigation graph, and bind the new objects in `shared`. No existing module is edited
beyond those two registrations.

Replacing a technical concern means implementing one interface and changing one binding:

| Concern             | Interface             | Current implementation      |
| ------------------- | --------------------- | --------------------------- |
| Sync transport      | `RemoteSyncSource`    | local-only no-op            |
| Payload protection  | `PayloadCipher`       | pass-through, AES-GCM ready |
| Key storage         | `KeyStore`            | platform secure storage     |
| Reminder delivery   | `ReminderScheduler`   | per-platform actual         |
| Export format       | `CalendarExporter`    | ICS / CSV / PDF writers     |
