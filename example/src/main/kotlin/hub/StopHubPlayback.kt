package hub

import item.Item
import playback.PlaybackService
import renderer.RendererService
import usecase.UseCase

class StopHubPlayback(
    private val hubRepository: HubRepository,
    private val playbackService: PlaybackService,
    private val rendererService: RendererService
) : UseCase<StopHubPlayback.Input, Unit> {
    data class Input(val item: Item.Loaded, val playbackItem: Item.Loaded?)

    override suspend fun execute(input: Input) = input.runCatching {
        if (playbackItem?.id == item.id) {
            playbackService.release().getOrThrow()

            playbackService.detachRenderer().getOrThrow()

            rendererService.reset(id = item.id).getOrThrow()

            hubRepository.updatePlaybackItem(item = null).getOrThrow()
        }
    }
}