package app.calio.data.mapper

import app.calio.model.BusyStatus
import app.calio.model.CalioColor
import app.calio.model.CategoryId
import app.calio.model.EventLocation
import app.calio.model.EventPatch
import app.calio.model.EventStatus
import app.calio.model.EventTimeRange
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Stores the deviations of a single occurrence as JSON.
 *
 * The transfer object below exists so the domain model stays free of serialisation annotations:
 * how a patch is written to disk is a storage concern, and tying the entity to a wire format would
 * make every future schema change a change to the model as well.
 */
internal object EventPatchCodec {

    private val json = Json {
        // A patch written by a newer version must not make an older one fail to read the row.
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(patch: EventPatch): String = json.encodeToString(patch.toDto())

    fun decode(text: String): EventPatch = json.decodeFromString<EventPatchDto>(text).toDomain()

    @Serializable
    private data class EventPatchDto(
        val title: String? = null,
        val description: String? = null,
        val notes: String? = null,
        val locationLabel: String? = null,
        val locationLat: Double? = null,
        val locationLon: Double? = null,
        val isAllDay: Boolean? = null,
        val startLocal: String? = null,
        val endLocal: String? = null,
        val timeZoneId: String? = null,
        val categoryId: String? = null,
        val colorOverride: Long? = null,
        val busyStatus: String? = null,
        val status: String? = null,
    )

    private fun EventPatch.toDto() = EventPatchDto(
        title = title,
        description = description,
        notes = notes,
        locationLabel = location?.label,
        locationLat = location?.latitude,
        locationLon = location?.longitude,
        isAllDay = timeRange?.isAllDay,
        startLocal = timeRange?.startLocalText,
        endLocal = timeRange?.endLocalText,
        timeZoneId = timeRange?.timeZoneIdOrNull,
        categoryId = categoryId?.value,
        colorOverride = colorOverride?.argb,
        busyStatus = busyStatus?.name,
        status = status?.name,
    )

    private fun EventPatchDto.toDomain() = EventPatch(
        title = title,
        description = description,
        notes = notes,
        location = locationLabel?.let { EventLocation(it, locationLat, locationLon) },
        timeRange = timeRange(),
        categoryId = categoryId?.let(::CategoryId),
        colorOverride = colorOverride?.let(::CalioColor),
        busyStatus = busyStatus?.let(BusyStatus::valueOf),
        status = status?.let(EventStatus::valueOf),
    )

    private fun EventPatchDto.timeRange(): EventTimeRange? {
        val start = startLocal ?: return null
        val end = endLocal ?: return null

        return if (isAllDay == true) {
            EventTimeRange.AllDay(LocalDate.parse(start), LocalDate.parse(end))
        } else {
            EventTimeRange.Zoned(
                start = LocalDateTime.parse(start),
                endExclusive = LocalDateTime.parse(end),
                timeZone = TimeZone.of(requireNotNull(timeZoneId) { "a zoned patch needs a time zone" }),
            )
        }
    }
}
