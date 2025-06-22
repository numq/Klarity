package notification.queue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

@Composable
fun rememberNotificationQueue(): NotificationQueue {
    val queue = remember<NotificationQueue> { DefaultQueue() }

    DisposableEffect(Unit) {
        onDispose {
            queue.close()
        }
    }

    return queue
}