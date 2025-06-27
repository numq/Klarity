package playlist

import item.Item
import usecase.UseCase

class SelectPlaylistItem(
    private val playlistService: PlaylistService,
) : UseCase<SelectPlaylistItem.Input, Unit> {
    data class Input(val item: Item.Loaded?, val selectedPlaylistItem: SelectedPlaylistItem)

    override suspend fun execute(input: Input) = input.runCatching {
        playlistService.selectItem(
            item = when {
                item == null || (selectedPlaylistItem as? SelectedPlaylistItem.Present)?.item?.id == item.id -> null

                else -> item
            }
        ).getOrThrow()
    }
}