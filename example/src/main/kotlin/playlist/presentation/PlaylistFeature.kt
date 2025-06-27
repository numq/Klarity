package playlist.presentation

import feature.Feature
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.ext.getFullName

class PlaylistFeature(reducer: PlaylistReducer) : Feature<PlaylistCommand, PlaylistState, PlaylistEvent>(
    initialState = PlaylistState(),
    coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    reducer = reducer
) {
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    private val jobs = mutableMapOf<String, Job>()

    init {
        coroutineScope.launch {
            events.collect { event ->
                val key = event::class.getFullName()

                jobs[key]?.cancel()

                when (event) {
                    is PlaylistEvent.HandlePlaylist -> event.playlist.onEach { playlist ->
                        execute(PlaylistCommand.UpdatePlaylist(playlist))
                    }.launchIn(this)

                    else -> null
                }?.let { job ->
                    jobs[key] = job
                }
            }
        }

        coroutineScope.launch {
            execute(PlaylistCommand.GetPlaylist)
        }

        invokeOnClose {
            coroutineScope.launch {
                execute(PlaylistCommand.CleanUp)

                coroutineScope.cancel()
            }
        }
    }
}