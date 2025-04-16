package playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.numq.klarity.core.event.PlayerEvent
import com.github.numq.klarity.core.player.KlarityPlayer
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.runBlocking
import notification.Notification
import java.io.File

@Composable
fun PlaylistScreen(
    openFileChooser: () -> List<File>,
    upload: (List<String>) -> List<File>,
    notify: (Notification) -> Unit,
) {
    val player = remember { KlarityPlayer.create() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        player.onSuccess { player ->
            DisposableEffect(Unit) {
                onDispose {
                    runBlocking {
                        player.close().getOrThrow()
                    }
                }
            }

            PlaylistScreenSuccess(
                player = player,
                openFileChooser = openFileChooser,
                upload = upload,
                notify = notify
            )
        }.onFailure { throwable ->
            PlaylistScreenFailure(throwable)
        }
    }
}