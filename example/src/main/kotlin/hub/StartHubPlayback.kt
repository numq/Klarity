package hub

import item.Item
import playback.PlaybackService
import renderer.RendererService
import usecase.UseCase

class StartHubPlayback(
    private val hubRepository: HubRepository,
    private val playbackService: PlaybackService,
    private val rendererService: RendererService,
    private val stopHubPreview: StopHubPreview
) : UseCase<StartHubPlayback.Input, Unit> {
    data class Input(val item: Item.Loaded, val previewItem: Item.Loaded?, val playbackItem: Item.Loaded?)

    override suspend fun execute(input: Input) = input.runCatching {
        stopHubPreview.execute(input = StopHubPreview.Input(item = item, previewItem = previewItem)).getOrThrow()

        when (playbackItem?.id) {
            null -> {
                playbackService.prepare(location = item.location).getOrThrow()

                rendererService.reset(id = item.id).getOrThrow()

                playbackService.attachRenderer(id = item.id).getOrThrow()

                playbackService.play().getOrThrow()

                hubRepository.updatePlaybackItem(item = item).getOrThrow()
            }

            item.id -> Unit

            else -> {
                playbackService.release().getOrThrow()

                playbackService.detachRenderer().getOrThrow()

                rendererService.reset(id = playbackItem.id).getOrThrow()

                playbackService.prepare(location = item.location).getOrThrow()

                rendererService.reset(id = item.id).getOrThrow()

                playbackService.attachRenderer(id = item.id).getOrThrow()

                playbackService.play().getOrThrow()

                hubRepository.updatePlaybackItem(item = item).getOrThrow()
            }
        }
    }
}