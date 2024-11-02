package playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.numq.klarity.core.player.KlarityPlayer
import com.github.numq.klarity.core.preview.PreviewManager
import notification.Notification
import java.io.File

@Composable
fun PlaylistScreen(
    openFileChooser: () -> List<File>,
    upload: (List<String>) -> List<File>,
    notify: (Notification) -> Unit,
) {
    val player = remember { KlarityPlayer.create() }

    val previewManager = remember { PreviewManager.create() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        player.mapCatching { player ->
            player to previewManager.getOrThrow()
        }.onSuccess { (player, previewManager) ->
            DisposableEffect(Unit) {
                onDispose {
                    player.close()
                    previewManager.close()
                }
            }
            PlaylistScreenSuccess(
                player = player,
                previewManager = previewManager,
                openFileChooser = openFileChooser,
                upload = upload,
                notify = notify
            )
        }.onFailure { throwable ->
            PlaylistScreenFailure(throwable)
        }
    }
}