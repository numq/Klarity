package feature

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

abstract class Feature<in Command, State, Event>(
    initialState: State,
    private val reducer: Reducer<Command, State, Event>,
) {
    val featureScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _state = MutableStateFlow(initialState)
    val state = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    suspend fun execute(command: Command) = runCatching {
        val (newState, events) = reducer.reduce(_state.value, command)

        _state.emit(newState)

        events.forEach { event -> _events.send(event) }
    }.isSuccess

    open fun <Command, State, Event> Feature<Command, State, Event>.invokeOnClose(block: () -> Unit = {}) {
        block()
    }

    fun close() {
        try {
            invokeOnClose()
        } finally {
            featureScope.cancel()
        }
    }
}