package queue

import kotlinx.coroutines.flow.StateFlow

interface MediaQueue<Item> {
    val isShuffled: StateFlow<Boolean>
    val repeatMode: StateFlow<RepeatMode>
    val hasPrevious: StateFlow<Boolean>
    val hasNext: StateFlow<Boolean>
    val items: StateFlow<List<Item>>
    val selectedItem: StateFlow<SelectedItem<Item>>
    suspend fun shuffle()
    suspend fun setRepeatMode(repeatMode: RepeatMode)
    suspend fun previous()
    suspend fun next()
    suspend fun select(item: Item?)
    suspend fun add(item: Item)
    suspend fun delete(item: Item)
    suspend fun replace(from: Item, to: Item)

    companion object {
        fun <Item> create(): MediaQueue<Item> = DefaultMediaQueue()
    }
}