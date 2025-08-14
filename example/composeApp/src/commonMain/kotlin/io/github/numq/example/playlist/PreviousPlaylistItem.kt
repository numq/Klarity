package io.github.numq.example.playlist

import io.github.numq.example.usecase.UseCase

class PreviousPlaylistItem(
    private val playlistService: PlaylistService,
) : UseCase<Unit, Unit> {
    override suspend fun execute(input: Unit) = playlistService.previous()
}