package io.github.numq.example.hub

import io.github.numq.example.item.Item
import io.github.numq.example.playback.PlaybackState

data class Hub(
    val items: List<Item> = emptyList(),
    val previewItem: Item.Loaded? = null,
    val playbackItem: Item.Loaded? = null,
    val playbackState: PlaybackState = PlaybackState.Empty,
)