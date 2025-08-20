package io.github.numq.example.playlist

import io.github.numq.example.item.Item
import io.github.numq.example.playback.PlaybackService
import io.github.numq.example.renderer.RendererService
import io.github.numq.example.usecase.UseCase
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

        val probe = playbackService.getProbe(location = location).getOrThrow()

        probe.runCatching {
            checkNotNull(this) { "Unsupported media" }

            with(probe) {
                rendererService.create(id = id, location = location, width = width, height = height).getOrThrow()

                rendererService.reset(id = id).getOrThrow()

                playlistRepository.updateItem(
                    updatedItem = Item.Loaded(
                        id = id, location = location, width = width, height = height, duration = duration
                    )
                ).getOrThrow()
            }
        }.recoverCatching { throwable ->
            playlistRepository.updateItem(
                updatedItem = Item.Failed(id = id, location = location, throwable = throwable)
            ).getOrThrow()
        }.getOrThrow()
    }
}