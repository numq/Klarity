package queue

import kotlinx.coroutines.flow.StateFlow

/**
 * MediaQueue provides a functionality for managing a queue of media items.
 * It supports operations such as shuffling, repeating, selecting, adding, and removing items,
 * as well as navigating between items in the queue.
 *
 * @param <Item> The type of the media items in the queue.
 */
interface MediaQueue<Item> {

    /**
     * A StateFlow containing the current list of media items in the queue.
     */
    val items: StateFlow<List<Item>>

    /**
     * A StateFlow that represents whether the queue is shuffled.
     * `true` if the queue is shuffled, `false` otherwise.
     */
    val isShuffled: StateFlow<Boolean>

    /**
     * A StateFlow that represents the current repeat mode.
     * Possible values are `NONE`, `CIRCULAR`, and `SINGLE`.
     */
    val repeatMode: StateFlow<RepeatMode>

    /**
     * A StateFlow that represents the currently selected item in the queue.
     * It can be `Absent` if no item is selected, or `Present` with the selected item.
     */
    val selectedItem: StateFlow<SelectedItem<Item>>

    /**
     * A StateFlow that indicates whether there is a previous item to navigate to in the queue.
     * `true` if there is a previous item, `false` otherwise.
     */
    val hasPrevious: StateFlow<Boolean>

    /**
     * A StateFlow that indicates whether there is a next item to navigate to in the queue.
     * `true` if there is a next item, `false` otherwise.
     */
    val hasNext: StateFlow<Boolean>

    /**
     * Shuffles the current queue of media items. If the queue is already shuffled, this operation resets it to the original order.
     */
    suspend fun shuffle()

    /**
     * Sets the repeat mode for the queue.
     *
     * @param repeatMode The desired repeat mode (NONE, CIRCULAR, SINGLE).
     */
    suspend fun setRepeatMode(repeatMode: RepeatMode)

    /**
     * Selects the previous item in the queue if available. The behavior depends on the current repeat mode.
     */
    suspend fun previous()

    /**
     * Selects the next item in the queue if available. The behavior depends on the current repeat mode.
     */
    suspend fun next()

    /**
     * Selects a specific item in the queue. If the item is null or not in the queue, the selection is reset to `Absent`.
     *
     * @param item The item to select, or null to reset the selection.
     */
    suspend fun select(item: Item?)

    /**
     * Adds a new item to the queue.
     *
     * @param item The item to add to the queue.
     */
    suspend fun add(item: Item)

    /**
     * Removes an item from the queue. If the removed item is currently selected, the selection will move to the next or previous item if available.
     *
     * @param item The item to remove from the queue.
     */
    suspend fun delete(item: Item)

    /**
     * Replaces an existing item in the queue with a new one. If the replaced item is currently selected, the selection moves to the new item.
     *
     * @param from The item to be replaced.
     * @param to The new item to replace the existing one.
     */
    suspend fun replace(from: Item, to: Item)

    companion object {

        /**
         * Creates a new instance of the default MediaQueue implementation.
         *
         * @param <Item> The type of the media items in the queue.
         * @return A new instance of MediaQueue.
         */
        fun <Item> create(): MediaQueue<Item> = DefaultMediaQueue()
    }
}