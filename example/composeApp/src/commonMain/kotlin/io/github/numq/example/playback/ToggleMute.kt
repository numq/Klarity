package io.github.numq.example.playback

import io.github.numq.example.usecase.UseCase

class ToggleMute(private val playbackService: PlaybackService) : UseCase<Unit, Unit> {
    override suspend fun execute(input: Unit) = playbackService.toggleMute()
}