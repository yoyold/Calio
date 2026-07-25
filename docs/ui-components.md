# User interface

## Design intent

Minimal, quiet, fast. The interface should disappear behind the content: a calendar's job is to show
time, not to show itself. Concretely:

- One accent colour; all other colour comes from the user's calendars and categories.
- No decorative borders, no gradients, no shadows beyond the two elevation levels that carry meaning.
- Density is high on desktop and comfortable on touch, driven by one density token, not by forks.
- Motion is short and functional: 150–250 ms, standard easing, always interruptible.

## Design tokens (`core/designsystem`)

| Token group  | Values                                                                 |
| ------------ | ---------------------------------------------------------------------- |
| Spacing      | 4 dp base grid: `xs 4`, `sm 8`, `md 12`, `lg 16`, `xl 24`, `xxl 32`     |
| Radius       | `sm 6`, `md 10`, `lg 16`, `full`                                       |
| Elevation    | `flat`, `raised` (menus, sheets), `overlay` (drag preview)              |
| Typography   | Display / Title / Body / Label, four sizes each, one variable font      |
| Motion       | `fast 150`, `standard 220`, `emphasised 320`, standard easing curves     |
| Colour       | Material 3 scheme, light and dark, plus Calio semantic roles            |

Semantic colour roles beyond the base scheme: `focusBlock`, `bufferBlock`, `conflict`,
`nonWorkingHours`, `nowIndicator`, `todayHighlight`, `dropTarget`.

Category and calendar colours are user data, so they are never hard-coded into components. A shared
`eventColors(event)` helper resolves override → category → calendar and derives the container and
text colours with a guaranteed contrast ratio, in both themes.

## Theming

- Light and dark schemes are complete and equally maintained; dark is not a filter over light.
- The theme follows the system by default and can be forced in settings.
- Android may use dynamic colour for the neutral surfaces; event colours stay user-defined.

## Component inventory

### Shell

| Component            | Purpose                                                       |
| -------------------- | ------------------------------------------------------------- |
| `CalioTheme`         | Provides tokens, colour scheme, typography, density            |
| `AdaptiveScaffold`   | Chooses bottom bar / rail / sidebar from the window size class |
| `CalioTopBar`        | Period title, today button, view switcher, search entry        |
| `CalendarSidebar`    | Mini month, calendar toggles, category filter                  |
| `ViewSwitcher`       | Day / week / month / year segmented control                    |

### Time grids

| Component            | Purpose                                                        |
| -------------------- | -------------------------------------------------------------- |
| `TimeGrid`           | Scrollable hour grid shared by day and week views               |
| `TimeAxis`           | Hour labels, working-hours shading                              |
| `DayColumn`          | One day inside the grid, hosts positioned events                |
| `AllDayRow`          | Pinned row above the grid for all-day and multi-day events      |
| `NowIndicator`       | Current-time line, only on days that contain "now"              |
| `EventBlock`         | An occurrence in a time grid, with overlap-aware layout          |
| `EventChip`          | Compact occurrence for month and year views                     |
| `MonthGrid`          | 6×7 day cells with overflow indicator                           |
| `YearGrid`           | 12 mini months with density shading                             |
| `MiniMonth`          | Compact month used in the sidebar and pickers                   |

Overlapping events are laid out by a pure `OverlapLayoutCalculator` in the domain layer that turns a
list of occurrences into column indices and widths. Keeping it out of the composable makes the
trickiest visual rule directly unit-testable.

### Editing

| Component               | Purpose                                                    |
| ----------------------- | ---------------------------------------------------------- |
| `EventEditorScaffold`   | Adaptive: full screen on compact, dialog on expanded        |
| `DateTimeField`         | Combined date and time entry with keyboard-first input      |
| `TimeZonePicker`        | Searchable zone list, shows the resulting local time        |
| `RecurrencePicker`      | Presets plus a custom interval builder, renders a summary   |
| `ReminderList`          | Repeatable rows with presets and a custom offset            |
| `CategoryPicker`        | Colour dots, inline creation                                |
| `CalendarPicker`        | Target calendar selection                                   |
| `AttendeeField`         | Chip input with response status                             |
| `AttachmentList`        | Add, preview and remove attachments                         |
| `ColorPicker`           | Palette plus custom colour                                  |
| `ConflictWarning`       | Inline, non-blocking overlap warning with the conflicting events |

### Tasks

`TaskRow`, `SubtaskList`, `ProgressRing`, `PriorityBadge`, `DueChip`, `TaskFilterBar`.

### Feedback and states

`EmptyState`, `LoadingPlaceholder` (skeleton, never a spinner over content), `ErrorBanner`,
`SyncStatusIndicator`, `ConflictBanner`, `UndoSnackbar`.

Destructive actions are optimistic with undo rather than confirmation dialogs, except for deleting a
whole calendar or a recurring series, where the scope question is asked explicitly:
this occurrence / this and following / the entire series.

## Interaction

### Desktop

- Drag and drop moves an occurrence; a drag preview follows the pointer and the target slot is
  highlighted. Dropping outside working hours is allowed but shaded.
- Dragging an event's top or bottom edge resizes it, snapping to 5-minute steps (15 while zoomed out).
- Click and drag on empty grid space creates an event over the dragged range.
- Right-click opens a context menu; hover shows a details popover after a short delay.
- Full keyboard operation, see [navigation](navigation.md).

### Android

- Tap opens, long-press starts a move with haptic feedback and auto-scroll near the edges.
- Horizontal swipe changes the period, with the pager keeping neighbouring periods pre-composed.
- Pinch on the day and week grids changes the hour zoom.
- Bottom sheets replace dialogs; the primary action stays reachable one-handed.

## Performance rules

These are treated as requirements, not optimisations, because they decide whether the app feels
instant:

- Grid contents come from a `Flow` that emits only the visible range plus one period on each side.
- Occurrence expansion runs off the main thread and its result is cached per (range, calendar set).
- Lists are keyed and use stable, immutable state types so recomposition stays local.
- No layout work happens during a drag; positions are computed once per slot change.
- The window used for the first frame is rendered from cached state, so start-up shows content, not a
  spinner.
