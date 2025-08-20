package io.github.numq.klarity.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.random.Random

internal class DefaultMediaQueue<Item, SelectedItem : Item> : MediaQueue<Item, SelectedItem> {
    private val _originalItems = mutableListOf<Item>()

    private val _shuffledItems = mutableListOf<Item>()

    override val items = MutableStateFlow<List<Item>>(emptyList())

    override val isShuffled = MutableStateFlow(false)

    override val repeatMode = MutableStateFlow(RepeatMode.NONE)

    override val selection = MutableStateFlow<MediaQueueSelection<SelectedItem>>(MediaQueueSelection.Absent())

    override val hasPrevious = MutableStateFlow(false)

    override val hasNext = MutableStateFlow(false)

    private val shuffleSeed = MutableStateFlow(Random.nextLong())

    @Suppress("UNCHECKED_CAST")
    private fun Item.asSelectedOrNull(): SelectedItem? = this as? SelectedItem

    private suspend fun updateStates() {
        val currentIndex = getCurrentIndex()

        val itemCount = items.value.size

        hasPrevious.emit(repeatMode.value != RepeatMode.NONE || currentIndex > 0)

        hasNext.emit(repeatMode.value != RepeatMode.NONE || currentIndex < itemCount - 1)
    }

    private fun getCurrentIndex() = (selection.value as? MediaQueueSelection.Present<SelectedItem>)?.let { present ->
        items.value.indexOfFirst { it == present.item }
    } ?: -1

    private suspend fun rebuildItemsList() = when {
        isShuffled.value -> _shuffledItems.toList()

        else -> _originalItems.toList()
    }.let { item ->
        items.emit(item)
    }

    override suspend fun setShuffleEnabled(enabled: Boolean) = runCatching {
        if (isShuffled.value == enabled) return@runCatching

        isShuffled.emit(enabled)

        if (enabled) {
            shuffleSeed.value = Random.nextLong()

            _shuffledItems.clear()

            _shuffledItems.addAll(_originalItems.shuffled(Random(shuffleSeed.value)))

        }

        rebuildItemsList()

        preserveSelectionAfterShuffle()

        updateStates()
    }

    private suspend fun preserveSelectionAfterShuffle() {
        (selection.value as? MediaQueueSelection.Present<SelectedItem>)?.let { present ->
            if (present.item in items.value) {
                selection.emit(MediaQueueSelection.Present(item = present.item))
            }
        }
    }

    override suspend fun setRepeatMode(repeatMode: RepeatMode) = runCatching {
        this.repeatMode.emit(repeatMode)

        updateStates()
    }

    override suspend fun previous() = navigate(-1)

    override suspend fun next() = navigate(1)

    private suspend fun navigate(offset: Int) = runCatching {
        if (items.value.isEmpty()) return@runCatching

        val currentIndex = getCurrentIndex()

        if (currentIndex < 0) return@runCatching

        val newIndex = when (repeatMode.value) {
            RepeatMode.NONE -> currentIndex + offset

            RepeatMode.CIRCULAR -> (currentIndex + offset).mod(items.value.size)

            RepeatMode.SINGLE -> currentIndex
        }

        items.value.getOrNull(newIndex)?.asSelectedOrNull()?.let { item ->
            selection.emit(MediaQueueSelection.Present(item = item))
        }

        updateStates()
    }

    override suspend fun select(item: Item?) = runCatching {
        val newSelection: MediaQueueSelection<SelectedItem> = item?.takeIf { item ->
            item in items.value
        }?.asSelectedOrNull()?.let { item ->
            MediaQueueSelection.Present(item = item)
        } ?: MediaQueueSelection.Absent()

        selection.emit(newSelection)

        updateStates()
    }

    override suspend fun add(item: Item) = runCatching {
        _originalItems.add(item)

        if (isShuffled.value) {
            _shuffledItems.add(Random(shuffleSeed.value).nextInt(_shuffledItems.size + 1), item)
        }

        rebuildItemsList()

        updateStates()
    }

    override suspend fun delete(item: Item) = runCatching {
        if (item in _originalItems) {
            val wasSelected = (selection.value as? MediaQueueSelection.Present<SelectedItem>)?.item == item

            _originalItems.remove(item)

            _shuffledItems.remove(item)

            rebuildItemsList()

            if (wasSelected) {
                if (items.value.isNotEmpty()) {
                    select(items.value.first())
                } else {
                    selection.emit(MediaQueueSelection.Absent())
                }
            }

            updateStates()
        }
    }

    override suspend fun replace(from: Item, to: Item) = runCatching {
        if (from in _originalItems) {
            val wasSelected = (selection.value as? MediaQueueSelection.Present<SelectedItem>)?.item == from

            _originalItems.replaceAll { if (it == from) to else it }

            _shuffledItems.replaceAll { if (it == from) to else it }

            rebuildItemsList()

            if (wasSelected) {
                select(to)
            }
        }
    }

    override suspend fun clear() = runCatching {
        _originalItems.clear()

        _shuffledItems.clear()

        items.emit(emptyList())

        selection.emit(MediaQueueSelection.Absent())

        updateStates()
    }
}