package io.github.numq.example.playlist.presentation

import io.github.numq.example.playlist.Playlist
import io.github.numq.klarity.renderer.Renderer

data class PlaylistState(
    val playlist: Playlist = Playlist(),
    val playbackRenderer: Pair<String, Renderer>? = null,
    val previewRenderer: Pair<String, Renderer>? = null,
    val previewTimestamp: PreviewTimestamp? = null,
    val isOverlayVisible: Boolean = true,
    val isPlaylistVisible: Boolean = false,
    val isFileChooserVisible: Boolean = false,
    val isInputDialogVisible: Boolean = false,
    val isDragAndDropActive: Boolean = false,
)