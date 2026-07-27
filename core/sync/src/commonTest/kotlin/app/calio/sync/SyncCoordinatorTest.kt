package app.calio.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorTest {

    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-08-05T08:30:00Z")
    }

    private val pending = MutableStateFlow(0)

    private class CountingRunner(
        private val outcome: SyncOutcome = SyncOutcome(pushed = 1),
        private val failure: Exception? = null,
    ) : SyncRunner {
        var rounds = 0
            private set

        override suspend fun sync(): SyncOutcome {
            rounds++
            failure?.let { throw it }
            return outcome
        }
    }

    private fun coordinator(runner: SyncRunner) = SyncCoordinator(
        runner = runner,
        pendingChanges = pending,
        clock = clock,
        changeDebounce = 200.milliseconds,
        pollInterval = 10.seconds,
    )

    @Test
    fun `a round records what it did`() = runTest {
        val runner = CountingRunner(SyncOutcome(pushed = 2, applied = 3))
        val coordinator = coordinator(runner)

        coordinator.syncOnce()

        assertEquals(1, runner.rounds)
        assertEquals(SyncOutcome(pushed = 2, applied = 3), coordinator.status.value.lastOutcome)
        assertNotNull(coordinator.status.value.lastSuccessAt)
        assertNull(coordinator.status.value.lastError)
    }

    @Test
    fun `a failed round is recorded rather than thrown`() = runTest {
        // The folder may sit on a drive that is not mounted yet. Nothing is lost by waiting.
        val coordinator = coordinator(CountingRunner(failure = IllegalStateException("no such folder")))

        coordinator.syncOnce()

        assertEquals("no such folder", coordinator.status.value.lastError)
        assertNull(coordinator.status.value.lastSuccessAt)
        assertTrue(!coordinator.status.value.isRunning)
    }

    @Test
    fun `a later success clears an earlier failure`() = runTest {
        var shouldFail = true
        val runner = SyncRunner {
            if (shouldFail) throw IllegalStateException("not yet") else SyncOutcome()
        }
        val coordinator = coordinator(runner)

        coordinator.syncOnce()
        assertNotNull(coordinator.status.value.lastError)

        shouldFail = false
        coordinator.syncOnce()

        assertNull(coordinator.status.value.lastError)
    }

    @Test
    fun `rounds never overlap`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var started = 0
        val coordinator = coordinator(
            SyncRunner {
                started++
                gate.await()
                SyncOutcome()
            },
        )

        backgroundScope.launch { coordinator.syncOnce() }
        runCurrent()
        // The second call finds the door locked and returns instead of queueing behind.
        coordinator.syncOnce()

        assertEquals(1, started)
        gate.complete(Unit)
    }

    @Test
    fun `a burst of local changes causes one round`() = runTest {
        val runner = CountingRunner()
        val coordinator = coordinator(runner)
        coordinator.start(backgroundScope)
        runCurrent()
        val afterStart = runner.rounds

        repeat(5) { index ->
            pending.value = index + 1
            advanceTimeBy(50)
        }
        advanceTimeBy(500)

        assertEquals(afterStart + 1, runner.rounds)
        coordinator.stop()
    }

    @Test
    fun `an empty outbox does not cause a round of its own`() = runTest {
        val runner = CountingRunner()
        val coordinator = coordinator(runner)
        coordinator.start(backgroundScope)
        runCurrent()
        val afterStart = runner.rounds

        pending.value = 0
        advanceTimeBy(1_000)

        assertEquals(afterStart, runner.rounds)
        coordinator.stop()
    }

    @Test
    fun `starting synchronises once straight away`() = runTest {
        val runner = CountingRunner()
        val coordinator = coordinator(runner)

        coordinator.start(backgroundScope)
        runCurrent()

        assertEquals(1, runner.rounds)
        coordinator.stop()
    }

    @Test
    fun `the timer keeps looking for changes made elsewhere`() = runTest {
        val runner = CountingRunner()
        val coordinator = coordinator(runner)
        coordinator.start(backgroundScope)
        runCurrent()

        advanceTimeBy(31.seconds.inWholeMilliseconds)

        assertTrue(runner.rounds >= 3)
        coordinator.stop()
    }

    @Test
    fun `asking for a round gets one`() = runTest {
        val runner = CountingRunner()
        val coordinator = coordinator(runner)
        coordinator.start(backgroundScope)
        runCurrent()
        val afterStart = runner.rounds

        coordinator.requestSync()
        runCurrent()

        assertEquals(afterStart + 1, runner.rounds)
        coordinator.stop()
    }

    @Test
    fun `the outbox size is reported for the interface to show`() = runTest {
        val coordinator = coordinator(CountingRunner())
        coordinator.start(backgroundScope)

        pending.value = 4
        runCurrent()

        assertEquals(4, coordinator.status.value.pendingChanges)
        coordinator.stop()
    }

    @Test
    fun `stopping ends the timer`() = runTest {
        val runner = CountingRunner()
        val coordinator = coordinator(runner)
        coordinator.start(backgroundScope)
        runCurrent()

        coordinator.stop()
        val afterStop = runner.rounds
        advanceTimeBy(60.seconds.inWholeMilliseconds)

        assertEquals(afterStop, runner.rounds)
    }
}
