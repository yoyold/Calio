# Calio

A fast, minimal, offline-first calendar and task application for **Windows** and **Android**,
built from a single Kotlin codebase.

## Highlights

- Day / week / month / year calendar views
- Recurring events with per-occurrence exceptions
- Multiple reminders per event
- Multiple calendars and colour categories
- Integrated tasks with subtasks and progress
- Focus blocks, buffer times, working hours, conflict detection, free-slot overview
- Full-text search across title, description, notes and location
- Offline-first storage with automatic multi-device synchronisation
- End-to-end encryption envelope for everything that leaves the device
- Export to ICS, CSV and PDF

## Technology

| Concern            | Choice                                              |
| ------------------ | --------------------------------------------------- |
| Language           | Kotlin (Multiplatform)                              |
| UI                 | Compose Multiplatform (Android + Desktop/JVM)       |
| Architecture       | Clean Architecture, MVVM per screen, unidirectional state |
| Persistence        | SQLDelight (SQLite) with FTS5                       |
| Async              | Coroutines / Flow                                   |
| Dependency wiring  | Constructor injection, single composition root      |
| Testing            | kotlin.test, coroutines-test, Turbine, in-memory SQLite |

## Documentation

| Document                                     | Content                                        |
| -------------------------------------------- | ---------------------------------------------- |
| [Architecture](docs/architecture.md)          | Layers, modules, dependency rules              |
| [Data model](docs/data-model.md)              | Domain entities and their invariants           |
| [Database](docs/database.md)                  | SQLite schema, indices, migrations             |
| [Synchronisation](docs/sync.md)               | Change log, conflict handling, encryption      |
| [Navigation](docs/navigation.md)              | Route graph and adaptive shell                 |
| [UI components](docs/ui-components.md)        | Design tokens and component inventory          |
| [Testing](docs/testing.md)                    | Test strategy per layer                        |
| [Decisions](docs/decisions.md)                | Decision log with rationale                    |

## Repository layout

```
apps/        Platform entry points (Android application, Desktop application)
shared/      Composition root shared by both entry points
core/        Technical building blocks, free of feature knowledge
domain/      Entities, repository contracts and use cases (pure Kotlin)
data/        Repository implementations, mappers, sync adapters
feature/     User-facing features (UI + view models)
build-logic/ Gradle convention plugins
docs/        Architecture and design documentation
```

## Building

Requirements: JDK 17 or newer and the Android SDK. Point the build at the SDK once by creating a
`local.properties` file in the repository root — it is machine specific and is not tracked:

```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

Then use the wrapper; no local Gradle installation is needed.

```bash
./gradlew check
```

Versions are declared exclusively in `gradle/libs.versions.toml`, and module configuration lives in
the convention plugins under `build-logic/`. A new module therefore needs a build script of a few
lines and never repeats toolchain or Android settings.
