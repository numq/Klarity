package io.github.numq.example.playlist

import io.github.numq.example.item.Item
import io.github.numq.example.usecase.UseCase

class RemovePlaylistItem(
    private val playlistRepository: PlaylistRepository,
) : UseCase<RemovePlaylistItem.Input, Unit> {
    data class Input(val item: Item)

    override suspend fun execute(input: Input) = playlistRepository.removeItem(item = input.item)
}