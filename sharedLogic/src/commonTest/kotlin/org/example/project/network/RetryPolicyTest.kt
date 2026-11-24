package org.example.project.network

import io.ktor.http.HttpMethod
import org.example.project.core.result.DataError
import org.example.project.core.result.TimeoutKind
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Pure decision table: which failures are retried, for which methods, after how long. */
class RetryPolicyTest {

    private val policy = RetryPolicy(maxRetries = 2)

    private val unsafeMethods = listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete)

    private val retryable = listOf(
        DataError.ConnectionLost(),
        DataError.Timeout(TimeoutKind.Connect),
        DataError.Timeout(TimeoutKind.Socket),
        DataError.Timeout(TimeoutKind.Request),
        DataError.Server(500),
        DataError.Server(502),
        DataError.Server(503),
    )

    private val notRetryable = listOf(
        DataError.NoInternet(),
        DataError.Maintenance(),
        DataError.UpgradeRequired(),
        DataError.Unauthorized(),
        DataError.Forbidden(),
        DataError.NotFound(),
        DataError.Validation(emptyMap()),
        DataError.Business(code = "RESTAURANT_CLOSED"),
        DataError.Server(405),              // an unexpected 4xx: asking again gives the same answer
        DataError.BadResponse(),
        DataError.Unknown(),
    )

    @Test
    fun transientFailuresAreRetriedOnSafeMethods() {
        retryable.forEach { error ->
            assertNotNull(policy.delayBeforeRetry(error, HttpMethod.Get, retriesSoFar = 0), "GET $error")
            assertNotNull(policy.delayBeforeRetry(error, HttpMethod.Head, retriesSoFar = 0), "HEAD $error")
        }
    }

    @Test
    fun unsafeMethodsAreNeverRetried() {
        val everyError = retryable + notRetryable + DataError.RateLimited(retryAfterSeconds = 1)
        unsafeMethods.forEach { method ->
            everyError.forEach { error ->
                assertNull(policy.delayBeforeRetry(error, method, retriesSoFar = 0), "${method.value} $error")
            }
        }
    }

    @Test
    fun failuresThatRetryingCannotFixAreNotRetried() {
        notRetryable.forEach { error ->
            assertNull(policy.delayBeforeRetry(error, HttpMethod.Get, retriesSoFar = 0), "$error")
        }
    }

    @Test
    fun stopsAfterMaxRetries() {
        val error = DataError.ConnectionLost()

        assertNotNull(policy.delayBeforeRetry(error, HttpMethod.Get, retriesSoFar = 1))
        assertNull(policy.delayBeforeRetry(error, HttpMethod.Get, retriesSoFar = 2))
        assertNull(RetryPolicy(maxRetries = 0).delayBeforeRetry(error, HttpMethod.Get, retriesSoFar = 0))
    }

    @Test
    fun delayDoublesAndStaysWithinTheJitter() {
        repeat(200) { seed ->
            val jittered = RetryPolicy(maxRetries = 2, random = Random(seed))
            val first = assertNotNull(jittered.delayBeforeRetry(DataError.Server(500), HttpMethod.Get, retriesSoFar = 0))
            val second = assertNotNull(jittered.delayBeforeRetry(DataError.Server(500), HttpMethod.Get, retriesSoFar = 1))

            assertTrue(first in 800.milliseconds..1_200.milliseconds, "first retry was $first")
            assertTrue(second in 1_600.milliseconds..2_400.milliseconds, "second retry was $second")
        }
    }

    @Test
    fun jitterMakesDevicesRetryAtDifferentMoments() {
        val delays = (0 until 50).map { seed ->
            RetryPolicy(maxRetries = 2, random = Random(seed))
                .delayBeforeRetry(DataError.Server(500), HttpMethod.Get, retriesSoFar = 0)
        }

        assertTrue(delays.toSet().size > 10, "delays were nearly identical: ${delays.toSet()}")
    }

    @Test
    fun rateLimitWaitsExactlyAsLongAsTheServerAsked() {
        assertEquals(2.seconds, policy.delayBeforeRetry(DataError.RateLimited(2), HttpMethod.Get, retriesSoFar = 0))
        assertEquals(10.seconds, policy.delayBeforeRetry(DataError.RateLimited(10), HttpMethod.Get, retriesSoFar = 0))
    }

    @Test
    fun rateLimitIsNotRetriedWhenTheWaitIsLongUnknownOrAlreadyUsed() {
        assertNull(policy.delayBeforeRetry(DataError.RateLimited(11), HttpMethod.Get, retriesSoFar = 0))
        assertNull(policy.delayBeforeRetry(DataError.RateLimited(null), HttpMethod.Get, retriesSoFar = 0))
        assertNull(policy.delayBeforeRetry(DataError.RateLimited(-1), HttpMethod.Get, retriesSoFar = 0))
        assertNull(policy.delayBeforeRetry(DataError.RateLimited(1), HttpMethod.Get, retriesSoFar = 1))
        assertNull(RetryPolicy(maxRetries = 0).delayBeforeRetry(DataError.RateLimited(1), HttpMethod.Get, retriesSoFar = 0))
    }
}
