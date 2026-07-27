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
    /**
     * Categories the user has switched off in the calendar.
     *
     * Hidden rather than deleted, and stored as the exception rather than as a visibility flag on
     * every category: a new category is visible without anyone having to say so.
     */
    val hiddenCategoryIds: Set<CategoryId> = emptySet(),
    /**
     * The folder two installations exchange changes through, or null when this one syncs with
     * nothing. A path rather than an account: a synced cloud folder needs no server of its own.
     */
    val syncFolderPath: String? = null,
) {
    fun isCategoryVisible(id: CategoryId?): Boolean = id == null || id !in hiddenCategoryIds
}
