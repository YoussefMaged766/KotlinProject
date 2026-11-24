package org.example.project.network

import io.ktor.http.HttpMethod
import org.example.project.core.result.DataError
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Decides whether a failed request is sent again, and after how long. Pure: it never waits itself.
 *
 * Only methods that change nothing on the server are retried. After a lost connection we can't know
 * whether a POST arrived, so sending it again could place an order twice; the user decides instead.
 */
internal class RetryPolicy(
    private val maxRetries: Int,
    private val random: Random = Random.Default,
) {

    /** How long to wait before the next attempt, or null to give up and report [error]. */
    fun delayBeforeRetry(error: DataError, method: HttpMethod, retriesSoFar: Int): Duration? {
        if (method !in SAFE_METHODS || retriesSoFar >= maxRetries) return null
        return when (error) {
            is DataError.ConnectionLost, is DataError.Timeout -> backoff(retriesSoFar)
            is DataError.Server -> if (error.status >= 500) backoff(retriesSoFar) else null
            is DataError.RateLimited -> rateLimitDelay(error, retriesSoFar)
            else -> null                        // offline, 4xx, maintenance, bad body: the answer won't change
        }
    }

    /** About 1 s, then 2 s, ±20% so devices that failed together don't all come back together. */
    private fun backoff(retriesSoFar: Int): Duration =
        FIRST_DELAY * (1 shl retriesSoFar) * random.nextDouble(1 - JITTER, 1 + JITTER)

    /** Exactly what the server asked for, once, and only if a user would still be waiting. */
    private fun rateLimitDelay(error: DataError.RateLimited, retriesSoFar: Int): Duration? {
        val wait = error.retryAfterSeconds?.seconds ?: return null
        return wait.takeIf { retriesSoFar == 0 && it in Duration.ZERO..MAX_RATE_LIMIT_WAIT }
    }

    private companion object {
        val SAFE_METHODS = setOf(HttpMethod.Get, HttpMethod.Head)
        val FIRST_DELAY = 1.seconds
        val MAX_RATE_LIMIT_WAIT = 10.seconds
        const val JITTER = 0.2
    }
}
