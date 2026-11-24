package org.example.project.core.events

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** What the app root needs: the events waiting for it, and a way to say it has reacted. */
interface GlobalEvents {

    /** What the app still has to react to, starting with whatever is already waiting. */
    val pending: Flow<Set<GlobalEvent>>

    /**
     * Call once the app has reacted: logged out (also when the user chose to), or "Try again" was tapped on the
     * maintenance screen. Whatever was sent before this can no longer raise [event].
     */
    fun handled(event: GlobalEvent)
}

/**
 * RequestExecutor raises the events here; apps only see [GlobalEvents].
 *
 * State rather than a one-shot stream: an event raised while nobody is collecting (cold start, rotation)
 * is still there afterwards, and ten requests finding the same problem make one event.
 *
 * Time is a counter that moves only in [handled]. A request remembers when it was sent, so a slow 401 from
 * the old session can't log out again once the first one has been handled.
 */
internal class GlobalEventBus : GlobalEvents {

    private data class State(
        val now: Int = 0,
        val pending: Set<GlobalEvent> = emptySet(),
        val handledAt: Map<GlobalEvent, Int> = emptyMap(),
    )

    private val state = MutableStateFlow(State())

    override val pending: Flow<Set<GlobalEvent>> = state.map { it.pending }.distinctUntilChanged()

    override fun handled(event: GlobalEvent) = state.update {
        val now = it.now + 1
        State(now, it.pending - event, it.handledAt + (event to now))
    }

    fun now(): Int = state.value.now

    /** @param sentAt [now] when the request that discovered [event] was sent. */
    fun raise(event: GlobalEvent, sentAt: Int) = state.update {
        val isOldNews = sentAt < (it.handledAt[event] ?: 0)
        if (isOldNews) it else it.copy(pending = it.pending + event)
    }
}
