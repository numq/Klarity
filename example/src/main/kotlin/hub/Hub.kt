package hub

import item.Item
import playback.PlaybackState

data class Hub(
    val items: List<Item> = emptyList(),
    val previewItem: Item.Loaded? = null,
    val playbackItem: Item.Loaded? = null,
    val playbackState: PlaybackState = PlaybackState.Empty,
)