package io.github.numq.example.playback

import io.github.numq.example.usecase.UseCase

class ChangeVolume(private val playbackService: PlaybackService) : UseCase<ChangeVolume.Input, Unit> {
    data class Input(val volume: Float)

    override suspend fun execute(input: Input) = playbackService.changeVolume(volume = input.volume)
}