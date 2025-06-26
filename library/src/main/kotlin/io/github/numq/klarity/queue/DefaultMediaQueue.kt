package io.github.numq.klarity.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Duration.Companion.nanoseconds

internal class DefaultMediaQueue<Item> : MediaQueue<Item> {
    private val mutex = Mutex()

    private val _originalItems = mutableListOf<Item>()

    private val _shuffledItems = mutableListOf<Item>()

    override val items = MutableStateFlow<List<Item>>(emptyList())

    override val isShuffled = MutableStateFlow(false)

    override val repeatMode = MutableStateFlow(RepeatMode.NONE)

    override val selectedItem = MutableStateFlow<SelectedItem>(SelectedItem.Absent)

    override val hasPrevious = MutableStateFlow(false)

    override val hasNext = MutableStateFlow(false)

    private var shuffleSeed: Long = Random.nextLong()

    private var shuffleRandom: Random = Random(shuffleSeed)

    private suspend fun updateStates() {
        val currentIndex = getCurrentIndex()

        val itemCount = items.value.size

        hasPrevious.emit(repeatMode.value != RepeatMode.NONE || currentIndex > 0)

        hasNext.emit(repeatMode.value != RepeatMode.NONE || currentIndex < itemCount - 1)
    }

    private fun getCurrentIndex() = (selectedItem.value as? SelectedItem.Present<*>)?.let { present ->
        items.value.indexOfFirst { it == present.item }
    } ?: -1

    private suspend fun rebuildItemsList() = when {
        isShuffled.value -> _shuffledItems.toList()

        else -> _originalItems.toList()
    }.let { item ->
        items.emit(item)
    }

    override suspend fun setShuffleEnabled(enabled: Boolean) = mutex.withLock {
        runCatching {
            if (isShuffled.value == enabled) return@runCatching

            isShuffled.emit(enabled)

            if (enabled) {
                shuffleSeed = Random.nextLong()

                shuffleRandom = Random(shuffleSeed)

                _shuffledItems.clear()

                _shuffledItems.addAll(_originalItems.shuffled(shuffleRandom))

            }

            rebuildItemsList()

            preserveSelectionAfterShuffle()

            updateStates()
        }
    }

    private suspend fun preserveSelectionAfterShuffle() {
        (selectedItem.value as? SelectedItem.Present<*>)?.let { present ->
            if (present.item in items.value) {
                selectedItem.emit(SelectedItem.Present(present.item, System.nanoTime().nanoseconds))
            }
        }
    }

    override suspend fun setRepeatMode(repeatMode: RepeatMode) = mutex.withLock {
        runCatching {
            this.repeatMode.emit(repeatMode)

            updateStates()
        }
    }

    override suspend fun previous() = mutex.withLock {
        navigate(-1)
    }

    override suspend fun next() = mutex.withLock {
        navigate(1)
    }

    private suspend fun navigate(offset: Int) = runCatching {
        if (items.value.isEmpty()) return@runCatching

        val currentIndex = getCurrentIndex()

        if (currentIndex < 0) return@runCatching

        val newIndex = when (repeatMode.value) {
            RepeatMode.NONE -> (currentIndex + offset).coerceIn(0, items.value.lastIndex)

            RepeatMode.CIRCULAR -> (currentIndex + offset).mod(items.value.size)

            RepeatMode.SINGLE -> currentIndex
        }

        items.value.getOrNull(newIndex)?.let { item ->
            selectedItem.emit(SelectedItem.Present(item, System.nanoTime().nanoseconds))
        }

        updateStates()
    }

    override suspend fun select(item: Item?) = mutex.withLock {
        runCatching {
            val newSelection = item?.takeIf { item ->
                item in items.value
            }?.let { item ->
                SelectedItem.Present(item, System.nanoTime().nanoseconds)
            } ?: SelectedItem.Absent

            selectedItem.emit(newSelection)

            updateStates()
        }
    }

    override suspend fun add(item: Item) = mutex.withLock {
        runCatching {
            _originalItems.add(item)

            if (isShuffled.value) {
                _shuffledItems.add(shuffleRandom.nextInt(_shuffledItems.size + 1), item)
            }

            rebuildItemsList()

            updateStates()
        }
    }

    override suspend fun delete(item: Item) = mutex.withLock {
        runCatching {
            if (item in _originalItems) {
                val wasSelected = (selectedItem.value as? SelectedItem.Present<*>)?.item == item

                _originalItems.remove(item)

                _shuffledItems.remove(item)

                rebuildItemsList()

                if (wasSelected) {
                    if (items.value.isNotEmpty()) {
                        select(items.value.first())
                    } else {
                        selectedItem.emit(SelectedItem.Absent)
                    }
                }

                updateStates()
            }
        }
    }

    override suspend fun replace(from: Item, to: Item) = mutex.withLock {
        runCatching {
            if (from in _originalItems) {
                val wasSelected = (selectedItem.value as? SelectedItem.Present<*>)?.item == from

                _originalItems.replaceAll { if (it == from) to else it }

                _shuffledItems.replaceAll { if (it == from) to else it }

                rebuildItemsList()

                if (wasSelected) {
                    select(to)
                }
            }
        }
    }

    override suspend fun clear() = mutex.withLock {
        runCatching {
            _originalItems.clear()

            _shuffledItems.clear()

            items.emit(emptyList())

            selectedItem.emit(SelectedItem.Absent)

            updateStates()
        }
    }
}