package feature

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class Feature<Command, State, Event>(
    initialState: State,
    private val reducer: Reducer<Command, State, Event>,
) {
    internal val coroutineScope = CoroutineScope(Dispatchers.Default)

    private val commands: Channel<Command> = Channel(Channel.UNLIMITED)

    private val _state = MutableStateFlow(initialState)

    val state = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)

    val events = _events.receiveAsFlow()

    init {
        coroutineScope.launch {
            commands.consumeEach { command ->
                val (newState, newEvents) = reducer.reduce(_state.value, command)

                _state.value = newState

                newEvents.forEach { event ->
                    _events.send(event)
                }
            }
        }
    }

    suspend fun execute(command: Command) = runCatching {
        commands.send(command)
    }.isSuccess

    open fun <Command, State, Event> Feature<Command, State, Event>.invokeOnClose(block: () -> Unit = {}) {
        block()
    }

    fun close() {
        try {
            invokeOnClose()
        } finally {
            coroutineScope.cancel()

            commands.close()

            _events.close()
        }
    }
}