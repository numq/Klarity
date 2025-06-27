package hub.presentation

import feature.Feature
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.ext.getFullName

class HubFeature(reducer: HubReducer) : Feature<HubCommand, HubState, HubEvent>(
    initialState = HubState(), coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()), reducer = reducer
) {
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    private val jobs = mutableMapOf<String, Job>()

    init {
        coroutineScope.launch {
            events.collect { event ->
                val key = event::class.getFullName()

                jobs[key]?.cancel()

                when (event) {
                    is HubEvent.HandleHub -> event.hub.onEach { hub ->
                        execute(HubCommand.UpdateHub(hub = hub))
                    }.launchIn(this)

                    else -> null
                }?.let { job ->
                    jobs[key] = job
                }
            }
        }

        coroutineScope.launch {
            execute(HubCommand.GetHub)
        }

        invokeOnClose {
            coroutineScope.launch {
                execute(HubCommand.CleanUp)

                coroutineScope.cancel()
            }
        }
    }
}