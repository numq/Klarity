package hub

import item.Item
import preview.LoopPreviewService
import renderer.RendererService
import usecase.UseCase

class StopHubPreview(
    private val hubRepository: HubRepository,
    private val rendererService: RendererService,
    private val loopPreviewService: LoopPreviewService
) : UseCase<StopHubPreview.Input, Unit> {
    data class Input(val item: Item.Loaded, val previewItem: Item.Loaded?)

    override suspend fun execute(input: Input) = input.runCatching {
        if (previewItem?.id == item.id) {
            loopPreviewService.stop().getOrThrow()

            rendererService.reset(id = item.id).getOrThrow()

            hubRepository.updatePreviewItem(item = null).getOrThrow()
        }
    }
}