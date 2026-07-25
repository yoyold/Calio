package app.calio.data.sync

import app.calio.database.CalioDatabase
import app.calio.domain.sync.RevisionSource
import app.calio.model.DeviceId
import app.calio.model.Revision
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * A hybrid logical clock, persisted alongside the synchronisation state.
 *
 * Two devices never agree on the time, so an ordinary timestamp cannot decide which of two competing
 * edits is newer. This clock keeps a physical component so revisions still read like times, a
 * counter for edits within the same millisecond, and is pushed forward by every remote revision it
 * is shown. The device id in the revision itself provides the final tiebreaker, which makes the
 * ordering total and identical on every device.
 *
 * The state is written back to the database on every step. Losing it would let a restart hand out a
 * revision that has already been used, and the duplicate would be resolved arbitrarily.
 */
class HybridLogicalClock(
    private val database: CalioDatabase,
    private val deviceId: DeviceId,
    private val clock: Clock = Clock.System,
) : RevisionSource {

    private val mutex = Mutex()
    private var physicalMillis = 0L
    private var counter = 0
    private var isLoaded = false

    override suspend fun next(): Revision = mutex.withLock {
        load()

        val now = clock.now().toEpochMilliseconds()
        if (now > physicalMillis) {
            physicalMillis = now
            counter = 0
        } else {
            // The wall clock stood still or went backwards; the counter keeps the ordering strict.
            counter++
        }

        persist()
        Revision.of(physicalMillis, counter, deviceId)
    }

    override suspend fun observe(remote: Revision) = mutex.withLock {
        load()

        val remoteMillis = remote.physicalMillis
        when {
            remoteMillis > physicalMillis -> {
                physicalMillis = remoteMillis
                counter = remote.counter + 1
            }

            remoteMillis == physicalMillis && remote.counter >= counter -> {
                counter = remote.counter + 1
            }

            else -> return@withLock
        }

        persist()
    }

    private fun load() {
        if (isLoaded) return

        database.syncQueries.initialiseSyncState(deviceId.value)
        val state = database.syncQueries.selectSyncState().executeAsOne()
        physicalMillis = state.clock_millis
        counter = state.clock_counter.toInt()
        isLoaded = true
    }

    private fun persist() {
        database.syncQueries.updateClock(
            clockMillis = physicalMillis,
            clockCounter = counter.toLong(),
        )
    }
}
