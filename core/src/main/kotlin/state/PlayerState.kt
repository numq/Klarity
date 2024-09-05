package state

import media.Media

sealed interface PlayerState {
    data object Empty : PlayerState

    sealed interface Ready : PlayerState {
        val media: Media

        data class Playing(override val media: Media) : Ready

        data class Paused(override val media: Media) : Ready

        data class Stopped(override val media: Media) : Ready

        data class Completed(override val media: Media) : Ready

        data class Seeking(override val media: Media) : Ready
    }
}