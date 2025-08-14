package io.github.numq.example.playback

import io.github.numq.example.usecase.UseCase

class ChangePlaybackSpeed(private val playbackService: PlaybackService) : UseCase<ChangePlaybackSpeed.Input, Unit> {
    sealed interface Input {
        data object Decrease : Input

        data object Increase : Input

        data object Reset : Input
    }

    override suspend fun execute(input: Input) = when (input) {
        is Input.Decrease -> playbackService.decreasePlaybackSpeed()

        is Input.Increase -> playbackService.increasePlaybackSpeed()

        is Input.Reset -> playbackService.resetPlaybackSpeed()
    }
}