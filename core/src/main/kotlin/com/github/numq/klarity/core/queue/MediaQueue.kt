package com.github.numq.klarity.core.queue

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
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun shuffle(): Result<Unit>

    /**
     * Sets the repeat mode for the queue.
     *
     * @param repeatMode The desired repeat mode (NONE, CIRCULAR, SINGLE).
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun setRepeatMode(repeatMode: RepeatMode): Result<Unit>

    /**
     * Selects the previous item in the queue if available. The behavior depends on the current repeat mode.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun previous(): Result<Unit>

    /**
     * Selects the next item in the queue if available. The behavior depends on the current repeat mode.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun next(): Result<Unit>

    /**
     * Selects a specific item in the queue. If the item is null or not in the queue, the selection is reset to `Absent`.
     *
     * @param item The item to select, or null to reset the selection.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun select(item: Item?): Result<Unit>

    /**
     * Adds a new item to the queue.
     *
     * @param item The item to add to the queue.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun add(item: Item): Result<Unit>

    /**
     * Removes an item from the queue. If the removed item is currently selected, the selection will move to the next or previous item if available.
     *
     * @param item The item to remove from the queue.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun delete(item: Item): Result<Unit>

    /**
     * Replaces an existing item in the queue with a new one. If the replaced item is currently selected, the selection moves to the new item.
     *
     * @param from The item to be replaced.
     * @param to The new item to replace the existing one.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun replace(from: Item, to: Item): Result<Unit>

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