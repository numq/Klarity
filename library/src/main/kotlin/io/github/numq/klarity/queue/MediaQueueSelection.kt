package io.github.numq.klarity.queue

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Represents the selection state of an item in the playlist-like queue.
 */
sealed interface MediaQueueSelection<SelectedItem> {
    val updatedAt: Duration

    /**
     * Indicates that no item is currently selected in the queue.
     *
     * @param updatedAt the time in [Duration] when the item was last updated or selected
     */
    data class Absent<SelectedItem>(
        override val updatedAt: Duration = System.nanoTime().nanoseconds
    ) : MediaQueueSelection<SelectedItem>

    /**
     * Represents an item that is currently selected in the queue.
     *
     * @param item the selected item
     * @param updatedAt the time in [Duration] when the item was last updated or selected
     */
    data class Present<SelectedItem>(
        val item: SelectedItem, override val updatedAt: Duration = System.nanoTime().nanoseconds
    ) : MediaQueueSelection<SelectedItem>
}