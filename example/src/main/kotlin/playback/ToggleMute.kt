package playback

import usecase.UseCase

class ToggleMute(private val playbackService: PlaybackService) : UseCase<Unit, Unit> {
    override suspend fun execute(input: Unit) = playbackService.toggleMute()
}