package hub

import item.Item
import usecase.UseCase

class RemoveHubItem(
    private val hubRepository: HubRepository,
) : UseCase<RemoveHubItem.Input, Unit> {
    data class Input(val item: Item)

    override suspend fun execute(input: Input) = hubRepository.removeItem(item = input.item)
}