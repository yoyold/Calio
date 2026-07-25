# Testing strategy

Tests are written together with the code they verify, not afterwards. Each layer has a default test
type; anything that deviates from it needs a reason.

| Layer            | Default test               | Runs against                              | Speed  |
| ---------------- | -------------------------- | ----------------------------------------- | ------ |
| `core/model`     | unit                       | pure functions, value objects             | µs     |
| `domain`         | unit, some property-based  | fake repositories                         | ms     |
| `data`           | integration                | real schema on an in-memory SQLite driver | ms     |
| `core/sync`      | integration                | two in-memory databases + file remote     | ms     |
| `feature/*`      | view-model unit tests      | fake use cases                            | ms     |
| UI               | Compose UI tests, selected | real composables, fake state              | s      |

## What gets the most attention

The parts where a mistake is silent and expensive:

1. **Recurrence expansion** — leap days, month-end clamping (31st in a 30-day month), daylight-saving
   transitions in both directions, `AfterCount` combined with cancelled occurrences, week start.
2. **Time zone handling** — an event created in one zone and displayed in another, all-day events
   near midnight, travel between zones.
3. **Conflict detection and free slots** — touching intervals, containment, all-day versus timed,
   `FREE` busy status excluded, working-hours boundaries.
4. **Sync convergence** — two devices, offline edits on both, arbitrary delivery order, deletes
   racing updates. Asserted by comparing the full state of both databases.
5. **Overlap layout** — the column assignment for overlapping events, verified as data, not pixels.

## Conventions

- Test names read as sentences: `` `all-day event keeps its date when the device zone changes` ``.
- One behaviour per test; no assertions on incidental fields.
- Fixtures come from `core/testing` builders with sensible defaults:
  `event(title = "Standup", start = "2026-08-03T09:00")`.
- Time is injected as a `Clock`; no test reads the real wall clock.
- Coroutine tests use a test dispatcher supplied through `DispatcherProvider`; no `delay` on the real
  clock, no flakiness by design.
- Flows are asserted with Turbine, so emissions and their order are explicit.

## Definition of done for a change

- The behaviour has a test that fails without the change.
- The full test suite passes locally.
- No new warning in the build.
- Public types in `domain` and `core` have a short KDoc explaining the *why*, not the *what*.
