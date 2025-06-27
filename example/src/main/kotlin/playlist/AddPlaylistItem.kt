package playlist

import item.Item
import playback.PlaybackService
import renderer.RendererService
import usecase.UseCase
import java.util.*

class AddPlaylistItem(
    private val playlistRepository: PlaylistRepository,
    private val playbackService: PlaybackService,
    private val rendererService: RendererService,
) : UseCase<AddPlaylistItem.Input, Unit> {
    data class Input(val location: String)

    override suspend fun execute(input: Input) = input.runCatching {
        val id = (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).toString()

        playlistRepository.addItem(item = Item.Loading(id = id, location = location)).getOrThrow()

        val duration = playbackService.getDuration(location = location).getOrThrow()

        runCatching {
            checkNotNull(duration) { "Unsupported media" }

            rendererService.add(id = id, location = location).getOrThrow()

            rendererService.reset(id = id).getOrThrow()

            playlistRepository.updateItem(
                updatedItem = Item.Loaded(id = id, location = location, duration = duration)
            ).getOrThrow()
        }.recoverCatching { throwable ->
            playlistRepository.updateItem(
                updatedItem = Item.Failed(id = id, location = location, throwable = throwable)
            ).getOrThrow()
        }.getOrThrow()
    }
}