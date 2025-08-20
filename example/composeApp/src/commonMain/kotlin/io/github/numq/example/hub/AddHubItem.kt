package io.github.numq.example.hub

import io.github.numq.example.item.Item
import io.github.numq.example.playback.PlaybackService
import io.github.numq.example.renderer.RendererService
import io.github.numq.example.usecase.UseCase
import java.util.*

class AddHubItem(
    private val hubRepository: HubRepository,
    private val playbackService: PlaybackService,
    private val rendererService: RendererService,
) : UseCase<AddHubItem.Input, Unit> {
    data class Input(val location: String)

    override suspend fun execute(input: Input) = input.runCatching {
        val id = (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).toString()

        hubRepository.addItem(item = Item.Loading(id = id, location = location)).getOrThrow()

        playbackService.getProbe(location = location).mapCatching { probe ->
            checkNotNull(probe) { "Unsupported media" }

            rendererService.create(
                id = id, location = location, width = probe.width, height = probe.height
            ).getOrThrow()

            rendererService.reset(id = id).getOrThrow()

            hubRepository.updateItem(
                updatedItem = Item.Loaded(
                    id = id, location = location, width = probe.width, height = probe.height, duration = probe.duration
                )
            ).getOrThrow()
        }.recoverCatching { throwable ->
            hubRepository.updateItem(
                updatedItem = Item.Failed(id = id, location = location, throwable = throwable)
            ).getOrThrow()
        }.getOrThrow()
    }
}