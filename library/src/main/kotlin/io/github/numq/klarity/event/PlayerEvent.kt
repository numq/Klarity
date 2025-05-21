package io.github.numq.klarity.event

/**
 * Sealed interface representing various events that can occur in the player.
 */
sealed interface PlayerEvent {
    /**
     * Represents an error event containing the exception that occurred.
     *
     * @property exception the exception that caused the error
     */
    data class Error(val exception: Exception) : PlayerEvent

    /**
     * Sealed interface representing buffer-related events.
     */
    sealed interface Buffer : PlayerEvent {
        /**
         * Represents a state indicating that the buffer has been filled and is ready.
         */
        data object Complete : Buffer
    }
}
