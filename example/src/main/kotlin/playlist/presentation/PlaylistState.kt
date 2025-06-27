package playlist.presentation

import playlist.Playlist

data class PlaylistState(
    val playlist: Playlist = Playlist(),
    val previewTimestamp: PreviewTimestamp? = null,
    val isPlaylistVisible: Boolean = false,
    val isFileChooserVisible: Boolean = false,
    val isInputDialogVisible: Boolean = false,
    val isDragAndDropActive: Boolean = false,
)