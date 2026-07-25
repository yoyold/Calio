package app.calio.domain.sync

import app.calio.model.Revision

/**
 * Hands out the logical timestamps that decide which of two competing edits wins.
 *
 * Wall clocks on two devices disagree, so an ordinary timestamp would let the device with the fast
 * clock win every conflict. A source of revisions must therefore be monotonic, must never repeat a
 * value, and must be pushed forward by anything it sees from another device — which is what
 * [observe] is for.
 */
interface RevisionSource {

    /** The next revision for a local mutation. Strictly greater than every revision handed out so far. */
    suspend fun next(): Revision

    /** Records a revision seen from another device so later local revisions sort after it. */
    suspend fun observe(remote: Revision)
}
