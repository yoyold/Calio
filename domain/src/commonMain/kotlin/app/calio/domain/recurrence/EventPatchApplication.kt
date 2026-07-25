package app.calio.domain.recurrence

import app.calio.model.Event
import app.calio.model.EventPatch

/**
 * Applies the deviations of a single occurrence to its series.
 *
 * A patch holds only the fields the user actually changed on that occurrence. Every other field
 * keeps following the series, so editing the series later still reaches them — which is the whole
 * reason exceptions are stored as patches rather than as full copies.
 */
internal fun Event.applyPatch(patch: EventPatch): Event = copy(
    title = patch.title ?: title,
    description = patch.description ?: description,
    notes = patch.notes ?: notes,
    location = patch.location ?: location,
    timeRange = patch.timeRange ?: timeRange,
    categoryId = patch.categoryId ?: categoryId,
    colorOverride = patch.colorOverride ?: colorOverride,
    busyStatus = patch.busyStatus ?: busyStatus,
    status = patch.status ?: status,
)
