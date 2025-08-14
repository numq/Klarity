package io.github.numq.example.hub

import io.github.numq.example.item.Item
import io.github.numq.example.preview.LoopPreviewService
import io.github.numq.example.usecase.UseCase

class StartHubPreview(
    private val hubRepository: HubRepository,
    private val loopPreviewService: LoopPreviewService
) : UseCase<StartHubPreview.Input, Unit> {
    data class Input(val item: Item.Loaded, val previewItem: Item.Loaded?, val playbackItem: Item.Loaded?)

    override suspend fun execute(input: Input) = input.runCatching {
        if (previewItem?.id == item.id || playbackItem?.id == item.id) {
            return@runCatching
        }

        loopPreviewService.start(item = item, rendererId = item.id).getOrThrow()

        hubRepository.updatePreviewItem(item = item).getOrThrow()
    }
}