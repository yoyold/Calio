package app.calio.sync

import app.calio.model.Revision

/** What to do with one incoming change. */
enum class Resolution {
    /** Nothing local competes with it. */
    Apply,

    /** Something local competes and loses, so the discarded version is kept for the user. */
    ApplyAndRecordConflict,

    /** The local version is newer, or the same one coming back. It stays and will be pushed. */
    KeepLocal,
}

/**
 * Decides what happens to an incoming change.
 *
 * The whole convergence of the system rests on this being a function of nothing but its arguments:
 * every device shown the same three values reaches the same answer, in any order, however often it
 * is repeated. That is why it is separated from the engine and tested as a table.
 *
 * Last writer wins, ordered by the hybrid logical clock rather than by a wall clock, so a device
 * with a fast clock cannot win every conflict. Ties go to the local side because a tie means the
 * same revision — the change coming back to the device that made it.
 *
 * Losing is not the same as being forgotten: when a local edit is overruled while it was still
 * waiting to be sent, the discarded version is recorded so the user can get it back. Blocking on it
 * instead would stop synchronisation for something the user has not asked about yet.
 */
fun resolveIncoming(
    local: Revision?,
    remote: Revision,
    hasPendingLocalChange: Boolean,
): Resolution = when {
    // Nothing here yet: whatever arrives is the whole truth.
    local == null -> Resolution.Apply

    remote <= local -> Resolution.KeepLocal

    hasPendingLocalChange -> Resolution.ApplyAndRecordConflict

    else -> Resolution.Apply
}
