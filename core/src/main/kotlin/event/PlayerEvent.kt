package event

sealed interface PlayerEvent {
    data class Error(val exception: Exception) : PlayerEvent

    sealed interface Buffer : PlayerEvent {
        data object Waiting : Buffer

        data object Complete : Buffer
    }
}