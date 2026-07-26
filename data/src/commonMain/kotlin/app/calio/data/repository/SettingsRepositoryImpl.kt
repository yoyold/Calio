package app.calio.data.repository

import app.calio.database.CalioDatabase
import app.calio.domain.repository.SettingsRepository
import app.calio.model.AppSettings
import app.calio.model.BufferPolicy
import app.calio.model.CategoryId
import app.calio.model.DayWindow
import app.calio.model.ThemeMode
import app.calio.model.WorkingHours
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SettingsRepositoryImpl(
    private val database: CalioDatabase,
    private val dispatcher: CoroutineDispatcher,
) : SettingsRepository {

    /**
     * A malformed or unreadable value falls back to the defaults instead of failing.
     *
     * Settings are not worth an unusable application: losing a working-hours setting is an
     * annoyance, refusing to start over one is a fault.
     */
    override fun observe(): Flow<AppSettings> =
        database.preferencesQueries.selectByKey(SETTINGS_KEY)
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { stored ->
                stored?.let { runCatching { json.decodeFromString<SettingsDto>(it).toDomain() }.getOrNull() }
                    ?: AppSettings()
            }

    override suspend fun update(settings: AppSettings): Unit = withContext(dispatcher) {
        // The generated parameter is `value_` because `value` is a Kotlin keyword in this position.
        database.preferencesQueries.put(
            key = SETTINGS_KEY,
            value_ = json.encodeToString(settings.toDto()),
        )
    }

    private companion object {
        const val SETTINGS_KEY = "app-settings"

        val json = Json {
            // A file written by a newer version must not stop an older one from starting.
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/**
 * The stored shape of the settings.
 *
 * Written by hand rather than by annotating the model, for the same reason the event patch is: how a
 * setting reaches the disk is a storage decision, and tying the entity to it would make every format
 * change a change to the model.
 */
@Serializable
private data class SettingsDto(
    val themeMode: String,
    val weekStart: Int,
    val workingHours: Map<Int, DayWindowDto>,
    val bufferPolicy: BufferPolicyDto,
    val hiddenCategoryIds: List<String> = emptyList(),
)

@Serializable
private data class DayWindowDto(val startMinute: Int, val endMinute: Int)

@Serializable
private data class BufferPolicyDto(
    val isEnabled: Boolean,
    val defaultMinutes: Int,
    val minimumGapMinutes: Int,
    val maximumGapMinutes: Int,
    val appliesToAllDayEvents: Boolean,
)

private val WEEK_DAYS = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

private const val MINUTES_PER_HOUR = 60

private fun AppSettings.toDto() = SettingsDto(
    themeMode = themeMode.name,
    weekStart = weekStart.ordinal,
    workingHours = workingHours.days.entries.associate { (day, window) ->
        day.ordinal to DayWindowDto(window.start.minuteOfDay(), window.endExclusive.minuteOfDay())
    },
    bufferPolicy = BufferPolicyDto(
        isEnabled = bufferPolicy.isEnabled,
        defaultMinutes = bufferPolicy.defaultMinutes,
        minimumGapMinutes = bufferPolicy.minimumGapMinutes,
        maximumGapMinutes = bufferPolicy.maximumGapMinutes,
        appliesToAllDayEvents = bufferPolicy.appliesToAllDayEvents,
    ),
    hiddenCategoryIds = hiddenCategoryIds.map { it.value },
)

private fun SettingsDto.toDomain() = AppSettings(
    themeMode = ThemeMode.valueOf(themeMode),
    weekStart = WEEK_DAYS[weekStart],
    workingHours = WorkingHours(
        workingHours.entries.associate { (day, window) ->
            WEEK_DAYS[day] to DayWindow(window.startMinute.asTime(), window.endMinute.asTime())
        },
    ),
    bufferPolicy = BufferPolicy(
        isEnabled = bufferPolicy.isEnabled,
        defaultMinutes = bufferPolicy.defaultMinutes,
        minimumGapMinutes = bufferPolicy.minimumGapMinutes,
        maximumGapMinutes = bufferPolicy.maximumGapMinutes,
        appliesToAllDayEvents = bufferPolicy.appliesToAllDayEvents,
    ),
    hiddenCategoryIds = hiddenCategoryIds.mapTo(mutableSetOf(), ::CategoryId),
)

// Times are stored as minutes from midnight: one integer, no parsing, and no way to write a value
// that is not a time of day.
private fun LocalTime.minuteOfDay(): Int = hour * MINUTES_PER_HOUR + minute

private fun Int.asTime(): LocalTime = LocalTime(this / MINUTES_PER_HOUR, this % MINUTES_PER_HOUR)
