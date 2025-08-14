package io.github.numq.example.hub.presentation

import io.github.numq.example.event.Event
import io.github.numq.example.hub.Hub
import kotlinx.coroutines.flow.Flow
import java.util.*

sealed class HubEvent private constructor() : Event<UUID> {
    override val key: UUID = UUID.randomUUID()

    data class Error(val message: String) : HubEvent()

    data class HandleHub(val hub: Flow<Hub>) : HubEvent()
}