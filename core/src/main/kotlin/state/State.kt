package state

import media.Media

sealed interface State {
    data object Empty : State

    sealed interface Loaded : State {
        val media: Media

        data class Playing(override val media: Media) : Loaded

        data class Paused(override val media: Media) : Loaded

        data class Stopped(override val media: Media) : Loaded

        data class Completed(override val media: Media) : Loaded

        data class Seeking(override val media: Media) : Loaded
    }
}