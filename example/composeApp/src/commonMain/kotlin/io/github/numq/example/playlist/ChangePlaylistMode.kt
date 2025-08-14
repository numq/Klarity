package io.github.numq.example.playlist

import io.github.numq.example.usecase.UseCase

class ChangePlaylistMode(
    private val playlistService: PlaylistService,
) : UseCase<ChangePlaylistMode.Input, Unit> {
    data class Input(val mode: PlaylistMode)

    override suspend fun execute(input: Input) = playlistService.setMode(mode = input.mode)
}