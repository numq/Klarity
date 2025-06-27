package playlist

import item.Item
import usecase.UseCase

class RemovePlaylistItem(
    private val playlistRepository: PlaylistRepository,
) : UseCase<RemovePlaylistItem.Input, Unit> {
    data class Input(val item: Item)

    override suspend fun execute(input: Input) = playlistRepository.removeItem(item = input.item)
}