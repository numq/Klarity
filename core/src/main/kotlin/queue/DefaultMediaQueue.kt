package queue

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlin.random.Random

internal class DefaultMediaQueue<Item> : MediaQueue<Item> {
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _items = MutableStateFlow(emptyList<Item>())
    override val items = MutableStateFlow(emptyList<Item>())

    private var shuffleSeed = MutableStateFlow<Long?>(null)
    override val isShuffled = shuffleSeed.map { seed ->
        seed != null
    }.stateIn(scope = coroutineScope, started = SharingStarted.Lazily, initialValue = false)

    override val repeatMode = MutableStateFlow(RepeatMode.NONE)
    override val selectedItem = MutableStateFlow<SelectedItem<Item>>(SelectedItem.Absent)
    override val hasPrevious = MutableStateFlow(false)
    override val hasNext = MutableStateFlow(false)

    private suspend fun updateStates() {
        val item = selectedItem.value
        val index = (item as? SelectedItem.Present<*>)?.let { items.value.indexOf(it.item) } ?: -1

        hasPrevious.emit(
            when {
                repeatMode.value == RepeatMode.NONE -> index > 0

                else -> items.value.isNotEmpty()
            }
        )
        hasNext.emit(
            when {
                repeatMode.value == RepeatMode.NONE -> index < items.value.lastIndex

                else -> items.value.isNotEmpty()
            }
        )
    }

    private suspend fun updateSelection(offset: Int) {
        if (items.value.isEmpty()) return

        (selectedItem.value as? SelectedItem.Present<*>)?.let { item ->
            val currentIndex = items.value.indexOf(item.item)
            if (currentIndex < 0) return

            val targetIndex = when (repeatMode.value) {
                RepeatMode.NONE -> currentIndex + offset

                RepeatMode.CIRCULAR -> (currentIndex + offset).mod(items.value.size)

                RepeatMode.SINGLE -> currentIndex
            }

            val newSelectedItem = items.value.getOrNull(targetIndex)?.let {
                SelectedItem.Present(it, System.nanoTime())
            } ?: SelectedItem.Absent

            selectedItem.emit(newSelectedItem)
        }
    }

    private suspend fun updateSelectionAfterModification(targetItem: Item) {
        (selectedItem.value as? SelectedItem.Present<*>)?.takeIf { it.item == targetItem }?.let {
            when {
                hasNext.value -> next()

                hasPrevious.value -> previous()

                else -> selectedItem.emit(SelectedItem.Absent)
            }
        }
    }

    init {
        merge(repeatMode, items, selectedItem).onEach {
            updateStates()
        }.launchIn(coroutineScope)

        _items.onEach { updatedItems ->
            items.emit(shuffleSeed.value?.let(::Random)?.let(updatedItems::shuffled) ?: updatedItems)
        }.launchIn(coroutineScope)

        shuffleSeed.onEach { seed ->
            items.emit(seed?.let(::Random)?.let(_items.value::shuffled) ?: _items.value)
        }.launchIn(coroutineScope)
    }

    override suspend fun shuffle() {
        shuffleSeed.emit(if (shuffleSeed.value == null) Random.nextLong() else null)
    }

    override suspend fun setRepeatMode(repeatMode: RepeatMode) {
        this.repeatMode.emit(repeatMode)
    }

    override suspend fun previous() = updateSelection(-1)

    override suspend fun next() = updateSelection(1)

    override suspend fun select(item: Item?) {
        val newItem = item?.takeIf { it in items.value }?.let {
            SelectedItem.Present(it, System.nanoTime())
        } ?: SelectedItem.Absent
        selectedItem.emit(newItem)
    }

    override suspend fun add(item: Item) {
        _items.emit(_items.value + item)
    }

    override suspend fun delete(item: Item) {
        if (item in items.value) {
            _items.emit(_items.value - item)
            updateSelectionAfterModification(item)
        }
    }

    override suspend fun replace(from: Item, to: Item) {
        if (from in items.value) {
            _items.emit(_items.value.map { if (it == from) to else it })
            updateSelectionAfterModification(from)
        }
    }
}