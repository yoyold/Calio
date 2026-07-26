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

    companion object {
        /**
         * Audit data for an entity that has not been stored yet.
         *
         * The revision is provisional: the repository replaces it when it writes, because handing
         * out logical timestamps is the storage layer's job and doing it here would let two entities
         * built in the same millisecond claim the same one.
         */
        fun forNewEntity(now: Instant, deviceId: DeviceId): AuditFields = AuditFields(
            createdAt = now,
            updatedAt = now,
            revision = Revision.of(now.toEpochMilliseconds(), 0, deviceId),
            originDevice = deviceId,
        )
    }
}
