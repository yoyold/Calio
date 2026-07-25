package app.calio.model

import kotlin.time.Instant

/**
 * Bookkeeping carried by every entity that takes part in synchronisation.
 *
 * [updatedAt] is the device wall clock and exists for display only; ordering decisions are always
 * made with [revision]. Deletions are soft so that a tombstone can still be propagated to other
 * devices — a row that simply disappeared would silently come back on the next sync.
 */
data class AuditFields(
    val createdAt: Instant,
    val updatedAt: Instant,
    val revision: Revision,
    val originDevice: DeviceId,
    val deletedAt: Instant? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null
}
