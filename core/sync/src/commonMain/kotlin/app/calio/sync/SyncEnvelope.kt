package app.calio.sync

import app.calio.model.Revision

enum class SyncedEntityType { CALENDAR, CATEGORY, EVENT, TASK }

enum class SyncOperation { UPSERT, DELETE }

/**
 * A payload as it leaves the device.
 *
 * The shape is encrypted from the first version even while the pass-through cipher is in use.
 * Retrofitting encryption once data has been synchronised would mean migrating every record on every
 * device and on the backend; carrying an empty envelope costs a few bytes and avoids that entirely.
 */
data class SealedPayload(
    val scheme: String,
    val ciphertext: String,
    val nonce: String? = null,
    val keyId: String? = null,
)

/**
 * One change, ready to travel.
 *
 * The remote is only ever asked to store this: a type, an opaque id, an ordering value and a blob.
 * It never needs to understand a calendar, which is what allows almost any storage to serve as a
 * backend — and what keeps the contents unreadable to it once the real cipher is switched on.
 */
data class SyncEnvelope(
    val entityType: SyncedEntityType,
    val entityId: String,
    val operation: SyncOperation,
    val revision: Revision,
    val payload: SealedPayload,
)

data class PushResult(val accepted: Int)

/**
 * A page of incoming changes.
 *
 * The cursor is opaque and belongs to the remote. Advancing it only after a batch has been committed
 * locally is what makes an interrupted synchronisation resumable rather than lossy.
 */
data class PullResult(
    val envelopes: List<SyncEnvelope>,
    val cursor: String?,
    val hasMore: Boolean = false,
)

/** What a synchronisation round did. */
data class SyncOutcome(
    val pushed: Int = 0,
    val applied: Int = 0,
    val skipped: Int = 0,
    val conflicts: Int = 0,
) {
    val changedAnything: Boolean get() = pushed > 0 || applied > 0
}
