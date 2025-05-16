package playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.numq.klarity.player.KlarityPlayer
import notification.Notification
import java.io.File

@Composable
fun PlaylistScreen(
    openFileChooser: () -> List<File>,
    upload: (List<String>) -> List<File>,
    notify: (Notification) -> Unit,
) {
    val player = remember { KlarityPlayer.create().getOrThrow() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PlaylistScreenSuccess(
            player = player,
            openFileChooser = openFileChooser,
            upload = upload,
            notify = notify
        )
    }
}