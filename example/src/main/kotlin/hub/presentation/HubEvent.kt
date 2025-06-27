package hub.presentation

import event.Event
import hub.Hub
import kotlinx.coroutines.flow.Flow
import java.util.*

sealed class HubEvent private constructor() : Event<UUID> {
    override val key: UUID = UUID.randomUUID()

    data class Error(val message: String) : HubEvent()

    data class HandleHub(val hub: Flow<Hub>) : HubEvent()
}