package app.calio.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** What the interface shows about synchronisation. */
data class SyncStatus(
    val isRunning: Boolean = false,
    val lastSuccessAt: Instant? = null,
    val lastOutcome: SyncOutcome? = null,
    val lastError: String? = null,
    val pendingChanges: Int = 0,
)

/**
 * Decides when to synchronise.
 *
 * Three triggers, all of them cheap: once when the application starts, shortly after a local change,
 * and on a slow timer for changes that arrive from elsewhere. Polling faster would not make another
 * device's edit appear sooner than that device's own push.
 *
 * A local change is debounced rather than acted on immediately. Editing an event writes several rows
 * in quick succession, and syncing after each one would send four half-finished versions of the same
 * change instead of one finished one.
 *
 * Rounds never overlap. A slow round simply means the next trigger finds the door locked and moves
 * on, which is the right behaviour: whatever it would have sent is still in the outbox afterwards.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SyncCoordinator(
    private val runner: SyncRunner,
    private val pendingChanges: Flow<Int>,
    private val clock: Clock = Clock.System,
    private val changeDebounce: Duration = DEFAULT_DEBOUNCE,
    private val pollInterval: Duration = DEFAULT_POLL_INTERVAL,
) {

    private val manualTriggers = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val running = Mutex()

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return

        job = scope.launch {
            launch { pendingChanges.collect { count -> _status.update { it.copy(pendingChanges = count) } } }

            launch {
                merge(
                    pendingChanges.filter { it > 0 }.debounce(changeDebounce),
                    manualTriggers,
                ).collect { syncOnce() }
            }

            launch {
                while (true) {
                    syncOnce()
                    delay(pollInterval)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Asks for a round now, for example because the user pressed a button. */
    fun requestSync() {
        manualTriggers.tryEmit(Unit)
    }

    suspend fun syncOnce() {
        // A round already under way is not queued behind: it will pick up whatever is pending when
        // it drains the outbox, so waiting would only duplicate work.
        if (!running.tryLock()) return

        try {
            _status.update { it.copy(isRunning = true, lastError = null) }
            val outcome = runner.sync()
            _status.update {
                it.copy(
                    isRunning = false,
                    lastSuccessAt = clock.now(),
                    lastOutcome = outcome,
                    lastError = null,
                )
            }
        } catch (failure: Exception) {
            // A failed round is normal: the folder may be on a drive that is not mounted yet.
            // Nothing is lost, the outbox still holds everything, and the next trigger tries again.
            _status.update {
                it.copy(isRunning = false, lastError = failure.message ?: "Synchronisation failed")
            }
        } finally {
            running.unlock()
        }
    }

    private companion object {
        /** Long enough to let one edit finish writing, short enough to feel immediate. */
        val DEFAULT_DEBOUNCE = 2.seconds

        /** Only for changes made elsewhere; the local ones are already covered by the debounce. */
        val DEFAULT_POLL_INTERVAL = 5.minutes
    }
}

private fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}
