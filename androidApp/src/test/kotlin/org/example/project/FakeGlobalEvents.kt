package org.example.project

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.example.project.core.events.GlobalEvent
import org.example.project.core.events.GlobalEvents

/** Test double: raises events the way the network layer would. */
class FakeGlobalEvents : GlobalEvents {
    override val pending = MutableStateFlow(emptySet<GlobalEvent>())

    fun raise(event: GlobalEvent) = pending.update { it + event }

    override fun handled(event: GlobalEvent) = pending.update { it - event }
}
