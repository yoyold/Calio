package app.calio.sync

import kotlin.time.Clock

/**
 * Reconciles the local database with a remote, in both directions.
 *
 * Push first, then pull. Sending what is already known locally before asking for news means the
 * other device's next round already sees this one's work, which halves how long two devices take to
 * agree.
 *
 * Both directions are idempotent and resumable. Interrupting a round loses nothing: outbox entries
 * are acknowledged only after the remote accepted them, and the cursor advances only after a batch
 * has been written locally. The worst an interruption costs is doing the same work twice.
 */
class SyncEngine(
    private val store: SyncableStore,
    private val remote: RemoteSyncSource,
    private val cipher: PayloadCipher,
    private val clock: Clock = Clock.System,
    private val pageSize: Int = RemoteSyncSource.DEFAULT_PAGE_SIZE,
) {

    suspend fun sync(): SyncOutcome {
        // Which records had an edit that had not left this device yet, captured before pushing.
        //
        // This is what makes a conflict a conflict. Asking after the push would always answer "no",
        // and the local version would be replaced without anyone being told; asking whether the
        // revisions differ would call every ordinary later edit from another device a conflict. An
        // edit that had not yet been seen elsewhere, overtaken by one that arrived meanwhile, is
        // exactly the case the user needs to hear about.
        val unsent = store.pendingChanges(Int.MAX_VALUE)
            .mapTo(mutableSetOf()) { it.entityType to it.entityId }

        val pushed = push()
        val pulled = pull(unsent)

        return SyncOutcome(
            pushed = pushed,
            applied = pulled.applied,
            skipped = pulled.skipped,
            conflicts = pulled.conflicts,
        )
    }

    suspend fun push(): Int {
        var pushed = 0

        while (true) {
            val pending = store.pendingChanges(pageSize)
            if (pending.isEmpty()) return pushed

            val envelopes = pending.map { change ->
                SyncEnvelope(
                    entityType = change.entityType,
                    entityId = change.entityId,
                    operation = change.operation,
                    revision = change.revision,
                    // A deletion carries the tombstone rather than nothing, so the other device
                    // learns which record died and when, not merely that something is missing.
                    payload = cipher.seal(
                        store.payloadFor(change.entityType, change.entityId).orEmpty(),
                    ),
                )
            }

            remote.push(envelopes)
            store.markPushed(pending.last().sequence, clock.now())
            pushed += pending.size

            if (pending.size < pageSize) return pushed
        }
    }

    private suspend fun pull(unsent: Set<Pair<SyncedEntityType, String>>): PullTally {
        var tally = PullTally()
        var cursor = store.cursor()

        while (true) {
            val page = remote.pull(cursor, pageSize)
            if (page.envelopes.isEmpty()) {
                // The cursor still moves: an empty page can mean the remote skipped this device's
                // own changes, and not advancing would fetch them again on every round.
                if (page.cursor != cursor) store.setCursor(page.cursor, clock.now())
                return tally
            }

            page.envelopes.forEach { envelope -> tally = tally.plus(apply(envelope, unsent)) }

            cursor = page.cursor
            store.setCursor(cursor, clock.now())

            if (!page.hasMore) return tally
        }
    }

    private suspend fun apply(
        envelope: SyncEnvelope,
        unsent: Set<Pair<SyncedEntityType, String>>,
    ): Outcome {
        // Every revision seen from elsewhere pushes the local clock past it, whether or not the
        // change is applied. Otherwise the next local edit could be handed a revision that sorts
        // before a change this device has already seen.
        store.observeRemoteRevision(envelope.revision)

        val plaintext = cipher.open(envelope.payload) ?: return Outcome.Skipped

        val local = store.localRevision(envelope.entityType, envelope.entityId)
        val wasUnsent = (envelope.entityType to envelope.entityId) in unsent

        return when (resolveIncoming(local, envelope.revision, wasUnsent)) {
            Resolution.KeepLocal -> Outcome.Skipped

            Resolution.Apply -> {
                store.applyRemote(envelope.toIncoming(plaintext))
                Outcome.Applied
            }

            Resolution.ApplyAndRecordConflict -> {
                val discarded = store.payloadFor(envelope.entityType, envelope.entityId).orEmpty()
                store.applyRemote(envelope.toIncoming(plaintext))
                store.recordConflict(
                    entityType = envelope.entityType,
                    entityId = envelope.entityId,
                    localRevision = requireNotNull(local) { "a conflict needs a local revision" },
                    remoteRevision = envelope.revision,
                    discardedPayload = discarded,
                    at = clock.now(),
                )
                Outcome.Conflicted
            }
        }
    }

    private enum class Outcome { Applied, Skipped, Conflicted }

    private data class PullTally(
        val applied: Int = 0,
        val skipped: Int = 0,
        val conflicts: Int = 0,
    ) {
        fun plus(outcome: Outcome): PullTally = when (outcome) {
            Outcome.Applied -> copy(applied = applied + 1)
            Outcome.Skipped -> copy(skipped = skipped + 1)
            Outcome.Conflicted -> copy(applied = applied + 1, conflicts = conflicts + 1)
        }
    }
}

private fun SyncEnvelope.toIncoming(plaintext: String) = IncomingChange(
    entityType = entityType,
    entityId = entityId,
    operation = operation,
    revision = revision,
    payload = plaintext,
)
