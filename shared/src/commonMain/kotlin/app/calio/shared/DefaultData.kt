package app.calio.shared

import app.calio.domain.repository.CalendarRepository
import app.calio.domain.repository.CategoryRepository
import app.calio.model.AuditFields
import app.calio.model.Calendar
import app.calio.model.CalendarId
import app.calio.model.CalioColor
import app.calio.model.Category
import app.calio.model.CategoryId
import app.calio.model.DeviceId
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/**
 * Creates the calendars and categories a fresh installation starts with.
 *
 * An empty calendar application is not usable: every event needs a calendar to live in, so one has
 * to exist before the first event can be created. The starting set is deliberately small — one
 * calendar and the five common categories — because deleting what you do not need is quicker than
 * inventing what you do.
 *
 * It runs only when there is nothing at all. On a device that has synchronised with another one the
 * calendars arrive from there, and seeding again would create duplicates that no merge could undo.
 */
class DefaultDataSeeder(
    private val calendars: CalendarRepository,
    private val categories: CategoryRepository,
    private val deviceId: DeviceId,
    private val clock: Clock = Clock.System,
) {

    suspend fun seedIfEmpty() {
        if (calendars.observeAll().first().isNotEmpty()) return

        val audit = AuditFields.forNewEntity(clock.now(), deviceId)

        calendars.upsert(
            Calendar(
                id = CalendarId("calendar-personal"),
                name = "Personal",
                color = CalioColor(0xFF1B6EF3),
                audit = audit,
                isDefault = true,
                sortOrder = 0,
            ),
        )

        if (categories.observeAll().first().isNotEmpty()) return

        BUILT_IN_CATEGORIES.forEachIndexed { index, (name, color) ->
            categories.upsert(
                Category(
                    id = CategoryId("category-${name.lowercase()}"),
                    name = name,
                    color = CalioColor(color),
                    audit = audit,
                    isBuiltIn = true,
                    sortOrder = index,
                ),
            )
        }
    }

    private companion object {
        val BUILT_IN_CATEGORIES = listOf(
            "Work" to 0xFF1B6EF3,
            "Private" to 0xFF4CAF50,
            "Family" to 0xFFE91E63,
            "Study" to 0xFF9C27B0,
            "Leisure" to 0xFFFF9800,
        )
    }
}
