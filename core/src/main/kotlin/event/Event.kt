package event

sealed interface Event {
    data class Error(val exception: Exception) : Event

    sealed interface Buffer : Event {
        data object Waiting : Playback

        data object Complete : Buffer
    }

    sealed interface Playback : Event {
        data object Resume : Playback
    }

    sealed interface Seeking : Event {
        data object Start : Seeking

        data object Complete : Seeking
    }
}