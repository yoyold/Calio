package app.calio.data.repository

import app.calio.data.TestEnvironment
import app.calio.model.AppSettings
import app.calio.model.BufferPolicy
import app.calio.model.DayWindow
import app.calio.model.ThemeMode
import app.calio.model.WorkingHours
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRepositoryTest {

    private val environment = TestEnvironment()
    private val settings = environment.settings

    @AfterTest
    fun tearDown() = environment.close()

    @Test
    fun `an unconfigured installation reads the defaults`() = runTest {
        val stored = settings.observe().first()

        assertEquals(AppSettings(), stored)
        assertEquals(ThemeMode.SYSTEM, stored.themeMode)
        assertEquals(DayOfWeek.MONDAY, stored.weekStart)
    }

    @Test
    fun `settings survive a round trip`() = runTest {
        val changed = AppSettings(
            themeMode = ThemeMode.DARK,
            weekStart = DayOfWeek.SUNDAY,
            workingHours = WorkingHours(
                mapOf(
                    DayOfWeek.TUESDAY to DayWindow(LocalTime(8, 30), LocalTime(16, 45)),
                    DayOfWeek.SATURDAY to DayWindow(LocalTime(10, 0), LocalTime(14, 0)),
                ),
            ),
            bufferPolicy = BufferPolicy(
                isEnabled = true,
                defaultMinutes = 15,
                minimumGapMinutes = 10,
                maximumGapMinutes = 90,
                appliesToAllDayEvents = true,
            ),
        )

        settings.update(changed)

        assertEquals(changed, settings.observe().first())
    }

    @Test
    fun `writing again replaces rather than adds`() = runTest {
        settings.update(AppSettings(themeMode = ThemeMode.DARK))
        settings.update(AppSettings(themeMode = ThemeMode.LIGHT))

        assertEquals(ThemeMode.LIGHT, settings.observe().first().themeMode)
    }

    @Test
    fun `an empty working week survives`() = runTest {
        val noWorkingDays = AppSettings(workingHours = WorkingHours(emptyMap()))

        settings.update(noWorkingDays)

        assertEquals(emptyMap(), settings.observe().first().workingHours.days)
    }

    @Test
    fun `an unreadable value falls back to the defaults instead of failing`() = runTest {
        // Refusing to start over a broken setting would be worse than losing the setting.
        environment.database.preferencesQueries.put(key = "app-settings", value_ = "{ not json")

        assertEquals(AppSettings(), settings.observe().first())
    }
}
