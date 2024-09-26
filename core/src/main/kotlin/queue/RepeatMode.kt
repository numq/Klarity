package queue

/**
 * RepeatMode defines the possible repeat modes for a playlist-like queue.
 */
enum class RepeatMode {
    /**
     * No repeat mode. The playlist will stop when it reaches the end.
     */
    NONE,

    /**
     * Circular repeat mode. The playlist will loop back to the beginning when it reaches the end.
     */
    CIRCULAR,

    /**
     * Single repeat mode. The currently selected item will repeat indefinitely.
     */
    SINGLE
}