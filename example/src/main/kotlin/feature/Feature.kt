package feature

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

abstract class Feature<in Command, State, Event>(
    val initialState: State,
    private val coroutineScope: CoroutineScope,
    private val reducer: Reducer<Command, State, Event>
) {
    private val mutex = Mutex()

    private val _state = MutableStateFlow(initialState)
    val state = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var onClose: (() -> Unit)? = null

    private suspend fun processCommand(command: Command) {
        val (newState, newEvents) = reducer.reduce(_state.value, command)

        _state.emit(newState)

        newEvents.forEach { event -> _events.send(event) }
    }

    suspend fun execute(command: Command) = mutex.withLock {
        processCommand(command)
    }

    fun invokeOnClose(block: () -> Unit) {
        onClose = block
    }

    fun close() {
        try {
            onClose?.invoke()
        } finally {
            coroutineScope.cancel()

            _events.close()
        }
    }
}