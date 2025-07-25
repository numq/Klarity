package playlist

import playback.PlaybackService
import playback.PlaybackState
import usecase.UseCase
import kotlin.time.Duration

class ControlPlaylistPlayback(
    private val playbackService: PlaybackService
) : UseCase<ControlPlaylistPlayback.PlaybackCommand, Unit> {
    sealed interface PlaybackCommand {
        data object Play : PlaybackCommand

        data object Pause : PlaybackCommand

        data object Resume : PlaybackCommand

        data object Stop : PlaybackCommand

        data class SeekTo(val timestamp: Duration, val playbackState: PlaybackState) : PlaybackCommand
    }

    override suspend fun execute(input: PlaybackCommand) = with(input) {
        when (this) {
            is PlaybackCommand.Play -> playbackService.play()

            is PlaybackCommand.Pause -> playbackService.pause()

            is PlaybackCommand.Resume -> playbackService.resume()

            is PlaybackCommand.Stop -> playbackService.stop()

            is PlaybackCommand.SeekTo -> {
                playbackService.seekTo(timestamp = timestamp).mapCatching {
                    if (playbackState is PlaybackState.Ready.Playing) {
                        playbackService.resume().getOrThrow()
                    }
                }
            }
        }
    }
}