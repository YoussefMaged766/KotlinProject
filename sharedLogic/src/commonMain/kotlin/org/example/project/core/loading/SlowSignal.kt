package org.example.project.core.loading

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Runs [block], calling [onSlow] with true if it takes [thresholdMillis] or longer, and with false once it is
 * over: finished, failed or cancelled. A fast block never calls it. Automatic retries count as one wait.
 */
suspend fun <T> withSlowSignal(
    thresholdMillis: Long,
    onSlow: (Boolean) -> Unit,
    block: suspend () -> T,
): T = coroutineScope {
    var wasSlow = false
    val timer = launch {
        delay(thresholdMillis.milliseconds)
        wasSlow = true
        onSlow(true)
    }
    try {
        block()
    } finally {
        // Join, not just cancel: on another thread the timer could otherwise say "slow" after we say it is over.
        withContext(NonCancellable) { timer.cancelAndJoin() }
        if (wasSlow) onSlow(false)
    }
}
