package io.github.numq.klarity.state

import io.github.numq.klarity.media.Media

/**
 * Sealed interface representing the possible states of the player.
 */
sealed interface PlayerState {
    /**
     * Represents the state when no media is loaded.
     */
    data object Empty : PlayerState

    /**
     * Represents the state when media is preparing.
     */
    data object Preparing : PlayerState

    /**
     * Represents the state when media is releasing.
     */
    data class Releasing(val previousState: PlayerState) : PlayerState

    /**
     * Represents the state when a fatal error occurred.
     */
    data class Error(val exception: Exception, val previousState: PlayerState) : PlayerState

    /**
     * Sealed interface representing the player states when media is ready.
     */
    sealed interface Ready : PlayerState {
        /**
         * The media currently being played or paused.
         */
        val media: Media

        /**
         * Represents the state when media is currently playing.
         */
        data class Playing(override val media: Media) : Ready

        /**
         * Represents the state when media is paused.
         */
        data class Paused(override val media: Media) : Ready

        /**
         * Represents the state when media has been stopped.
         */
        data class Stopped(override val media: Media) : Ready

        /**
         * Represents the state when media playback has completed.
         */
        data class Completed(override val media: Media) : Ready

        /**
         * Represents the state when media is currently seeking to a different position.
         */
        data class Seeking(override val media: Media) : Ready
    }
}