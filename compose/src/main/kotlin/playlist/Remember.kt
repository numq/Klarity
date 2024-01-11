package playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun rememberPlaylist(
    initialShuffled: Boolean = false,
    initialRepeatMode: Playlist.RepeatMode = Playlist.RepeatMode.NONE,
): Playlist {
    val playlist = rememberSaveable {
        Playlist.create(initialShuffled, initialRepeatMode)
    }

    DisposableEffect(Unit) { onDispose(playlist::close) }

    return playlist
}