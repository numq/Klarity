package hub

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import playback.PlaybackService
import playback.PlaybackState
import usecase.UseCase

class GetHub(
    private val hubRepository: HubRepository,
    private val playbackService: PlaybackService,
    private val stopHubPlayback: StopHubPlayback
) : UseCase<Unit, Flow<Hub>> {
    override suspend fun execute(input: Unit) = runCatching {
        hubRepository.hub.onEach { hub ->
            if (hub.playbackState !is PlaybackState.Ready.Playing) {
                playbackService.resetPlaybackSpeed().getOrThrow()
            }

            if (hub.playbackItem != null && hub.playbackState is PlaybackState.Ready.Completed) {
                stopHubPlayback.execute(
                    input = StopHubPlayback.Input(
                        item = hub.playbackItem,
                        playbackItem = hub.playbackItem
                    )
                ).getOrThrow()
            }
        }
    }
}