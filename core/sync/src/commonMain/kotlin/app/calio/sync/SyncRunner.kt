package app.calio.sync

/**
 * Something that can run a synchronisation round.
 *
 * The scheduler is written against this rather than against the engine, so it can be exercised
 * against a round that fails, or one that takes a while, without a database or a remote in sight.
 */
fun interface SyncRunner {
    suspend fun sync(): SyncOutcome
}