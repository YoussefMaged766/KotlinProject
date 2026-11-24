package org.example.project.core.loading

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** R6, on virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class SlowSignalTest {

    private val signals = mutableListOf<Boolean>()

    private suspend fun <T> watch(block: suspend () -> T): T =
        withSlowSignal(thresholdMillis = 3_000, onSlow = { signals += it }, block)

    @Test
    fun aFastResultIsNeverSlow() = runTest {
        assertEquals("data", watch { delay(2_900); "data" })

        advanceTimeBy(10_000)                       // the timer must not fire later either
        assertTrue(signals.isEmpty())
    }

    @Test
    fun itIsSlowFromTheThresholdUntilTheResult() = runTest {
        val load = launch { watch { delay(5_000) } }

        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(emptyList(), signals)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(true), signals)

        load.join()
        assertEquals(listOf(true, false), signals)
        assertEquals(5_000, currentTime)            // watching never delays the result
    }

    @Test
    fun leavingTheScreenClearsIt() = runTest {
        val load = launch { watch { delay(60_000) } }
        advanceTimeBy(4_000)

        load.cancel()
        load.join()

        assertTrue(load.isCancelled)
        assertEquals(listOf(true, false), signals)
    }

    @Test
    fun leavingBeforeTheThresholdSignalsNothing() = runTest {
        val load = launch { watch { delay(60_000) } }
        advanceTimeBy(1_000)

        load.cancel()
        advanceTimeBy(10_000)

        assertTrue(signals.isEmpty())
    }

    @Test
    fun aCrashClearsItAndIsPassedOn() = runTest {
        assertFailsWith<IllegalStateException> {
            watch { delay(4_000); error("boom") }
        }
        assertEquals(listOf(true, false), signals)
    }

    @Test
    fun theLastSignalIsNeverSlowEvenAcrossThreads() = runTest {
        // On real threads: the result arrives while the timer is still inside onSlow(true).
        withContext(Dispatchers.Default) {
            var shown = false
            val timerIsSignalling = CompletableDeferred<Unit>()

            withSlowSignal(
                thresholdMillis = 0,
                onSlow = { slow ->
                    if (slow) {
                        timerIsSignalling.complete(Unit)
                        val start = TimeSource.Monotonic.markNow()
                        while (start.elapsedNow() < 100.milliseconds) { /* busy */ }   // keep the timer's thread busy
                    }
                    shown = slow
                },
            ) { timerIsSignalling.await() }

            assertEquals(false, shown)
        }
    }
}
