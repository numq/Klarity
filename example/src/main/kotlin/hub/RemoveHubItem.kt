package hub

import item.Item
import usecase.UseCase

class RemoveHubItem(
    private val hubRepository: HubRepository, private val stopHubPlayback: StopHubPlayback
) : UseCase<RemoveHubItem.Input, Unit> {
    data class Input(val item: Item, val playbackItem: Item.Loaded?)

    override suspend fun execute(input: Input) = input.runCatching {
        if (item is Item.Loaded) {
            stopHubPlayback.execute(
                input = StopHubPlayback.Input(item = item, playbackItem = input.playbackItem)
            ).getOrThrow()
        }

        hubRepository.removeItem(item = input.item).getOrThrow()
    }
}