package io.github.numq.klarity.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

internal class DefaultMediaQueue<Item> : MediaQueue<Item> {
    private val mutex = Mutex()

    private var shuffleSeed: Long? = null

    private val _items = mutableListOf<Item>()

    override val items = MutableStateFlow<List<Item>>(emptyList())

    override val isShuffled = MutableStateFlow(false)

    override val repeatMode = MutableStateFlow(RepeatMode.NONE)

    override val selectedItem = MutableStateFlow<SelectedItem>(SelectedItem.Absent)

    override val hasPrevious = MutableStateFlow(false)

    override val hasNext = MutableStateFlow(false)

    private suspend fun updateStates() {
        val currentItem = selectedItem.value

        val index = (currentItem as? SelectedItem.Present<*>)?.let { items.value.indexOf(it.item) } ?: -1

        hasPrevious.emit(repeatMode.value != RepeatMode.NONE || index > 0)

        hasNext.emit(repeatMode.value != RepeatMode.NONE || index < items.value.lastIndex)
    }

    private suspend fun updateSelection(offset: Int) {
        if (_items.isEmpty()) return

        if (selectedItem.value is SelectedItem.Present<*>) {
            val currentIndex = items.value.indexOf((selectedItem.value as SelectedItem.Present<*>).item)

            if (currentIndex < 0) return

            val targetIndex = when (repeatMode.value) {
                RepeatMode.NONE -> currentIndex + offset

                RepeatMode.CIRCULAR -> (currentIndex + offset).mod(items.value.size)

                RepeatMode.SINGLE -> currentIndex
            }

            val newSelectedItem = items.value.getOrNull(targetIndex)?.let { item ->
                SelectedItem.Present(item, System.nanoTime())
            } ?: SelectedItem.Absent

            selectedItem.emit(newSelectedItem)
        }
    }

    override suspend fun setShuffleEnabled(enabled: Boolean) = mutex.withLock {
        runCatching {
            shuffleSeed = if (enabled) Random.nextLong() else null

            isShuffled.emit(enabled)

            val shuffledItems = if (enabled) {
                _items.shuffled(Random(shuffleSeed!!))
            } else {
                _items.toList()
            }

            items.emit(shuffledItems)

            updateStates()
        }
    }

    override suspend fun setRepeatMode(repeatMode: RepeatMode) = runCatching {
        this.repeatMode.emit(repeatMode)

        updateStates()
    }

    override suspend fun previous() = runCatching {
        updateSelection(-1)

        updateStates()
    }

    override suspend fun next() = runCatching {
        updateSelection(1)

        updateStates()
    }

    override suspend fun select(item: Item?) = runCatching {
        val newItem = item?.takeIf { it in items.value }?.let {
            SelectedItem.Present(it, System.nanoTime())
        } ?: SelectedItem.Absent

        selectedItem.emit(newItem)

        updateStates()
    }

    override suspend fun add(item: Item) = mutex.withLock {
        runCatching {
            _items += item

            items.emit(_items.toList())

            updateStates()
        }
    }

    override suspend fun delete(item: Item) = mutex.withLock {
        runCatching {
            if (item in _items) {
                val currentSelectedItem = selectedItem.value

                if (currentSelectedItem is SelectedItem.Present<*> && currentSelectedItem.item == item) {
                    when {
                        hasNext.value -> next()

                        hasPrevious.value -> previous()

                        else -> selectedItem.emit(SelectedItem.Absent)
                    }
                }

                _items -= item

                items.emit(_items.toList())

                updateStates()
            }
        }
    }

    override suspend fun replace(from: Item, to: Item) = mutex.withLock {
        runCatching {
            if (from in _items) {
                if (selectedItem.value is SelectedItem.Present<*> && selectedItem.value == from) {
                    select(to)
                }

                _items.replaceAll { if (it == from) to else it }

                items.emit(_items.toList())
            }
        }
    }
}
