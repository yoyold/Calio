package app.calio.model

import kotlinx.datetime.DayOfWeek

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Everything the user can configure.
 *
 * One object rather than a bag of separate keys: a screen that changes two settings at once should
 * not be able to leave the two disagreeing, and a single object makes that impossible by
 * construction.
 *
 * The defaults are the ones a fresh installation runs on, so nothing has to be written before the
 * application is usable.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val workingHours: WorkingHours = WorkingHours.Default,
    val bufferPolicy: BufferPolicy = BufferPolicy(),
)
