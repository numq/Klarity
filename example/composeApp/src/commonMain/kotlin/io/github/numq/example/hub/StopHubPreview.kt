package io.github.numq.example.hub

import io.github.numq.example.item.Item
import io.github.numq.example.preview.LoopPreviewService
import io.github.numq.example.usecase.UseCase

class StopHubPreview(
    private val hubRepository: HubRepository, private val loopPreviewService: LoopPreviewService
) : UseCase<StopHubPreview.Input, Unit> {
    data class Input(val item: Item.Loaded, val previewItem: Item.Loaded?)

    override suspend fun execute(input: Input) = input.runCatching {
        if (previewItem?.id == item.id) {
            loopPreviewService.stop().getOrThrow()

            hubRepository.updatePreviewItem(item = null).getOrThrow()
        }
    }
}