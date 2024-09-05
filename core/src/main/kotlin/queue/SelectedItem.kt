package queue

sealed interface SelectedItem<out Item> {
    data object Absent : SelectedItem<Nothing>

    data class Present<out Item>(val item: Item, val updatedAtNanos: Long) : SelectedItem<Item>
}