# Navigation

## Adaptive shell

One navigation graph serves both platforms; only the chrome around it changes, driven by window size
class rather than by platform checks. This keeps a resized desktop window and a tablet behaving
identically.

| Width class | Shell                                             | Typical device            |
| ----------- | ------------------------------------------------- | ------------------------- |
| Compact     | bottom navigation bar, full-screen detail, FAB     | phone portrait            |
| Medium      | navigation rail, detail as bottom sheet            | tablet, small window      |
| Expanded    | permanent sidebar + list/detail two-pane layout    | Windows desktop           |

The sidebar in the expanded layout also hosts the mini month, the calendar visibility toggles and the
category filter, none of which get their own route.

## Route graph

Routes are `@Serializable` objects and data classes, so arguments are type-checked at compile time
instead of being stringly-typed.

```
CalendarGraph
├── Calendar(view: CalendarView, date: LocalDate)   // DAY | WEEK | MONTH | YEAR
├── EventDetail(eventId, occurrenceStart?)
└── EventEditor(eventId?, prefillStart?, prefillCalendarId?)

TasksGraph
├── Tasks(filter: TaskFilter)
└── TaskEditor(taskId?, prefillDue?)

SearchGraph
└── Search(query?)

SettingsGraph
├── Settings
├── CalendarManagement
├── CategoryManagement
├── WorkingHours
├── FocusAndBuffers
├── SyncSettings
└── BackupAndExport
```

Design notes:

- The four calendar views are **one route with a parameter**, not four routes. Switching from week to
  month keeps the anchor date, animates in place and does not grow the back stack, which is what
  users expect from a calendar.
- `occurrenceStart` is required to open a single occurrence of a recurring series; without it the
  detail screen cannot know which instance is meant.
- The editor is one route for create and edit. A null id means create; the prefill arguments carry
  the slot the user tapped in the grid.

## Back behaviour

| Situation                          | Result                                                     |
| ---------------------------------- | ---------------------------------------------------------- |
| Detail open in two-pane layout     | closes the detail pane, list stays                         |
| Editor with unsaved changes        | confirmation dialog before discarding                      |
| Calendar view switched             | back returns to the previous view and date                 |
| Deep link into an occurrence       | synthesises a calendar back stack at that date             |

## Deep links

Used by reminder notifications and, later, by ICS file association:

```
calio://event/{eventId}?occurrence={isoDateTime}
calio://task/{taskId}
calio://calendar/{view}/{isoDate}
```

Tapping a reminder opens the occurrence with the calendar behind it, so back leads somewhere sensible
instead of closing the app.

## Abstraction

Navigation is used through a thin `Navigator` interface owned by `core/ui`:

```kotlin
interface Navigator {
    fun navigate(route: Route)
    fun navigateUp()
}
```

Feature modules depend on `Navigator` and on their own route types only. They never reference the
navigation library or another feature's routes, which is what keeps features independent and lets the
navigation library be replaced in one place.

## Keyboard and pointer (desktop)

Navigation is fully reachable without the mouse, because a calendar is used at speed:

| Shortcut         | Action                          |
| ---------------- | ------------------------------- |
| `D` `W` `M` `Y`  | switch view                     |
| `T`              | jump to today                   |
| `←` `→`          | previous / next period          |
| `Ctrl+N`         | new event                       |
| `Ctrl+Shift+N`   | new task                        |
| `Ctrl+F`         | search                          |
| `Esc`            | close detail or editor          |
| `Del`            | delete the selected event       |
