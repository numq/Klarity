package notification.queue

import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.channels.Channel
import notification.NotificationItem

interface NotificationQueue {
    val notifications: Channel<NotificationItem>

    fun push(message: String, label: ImageVector? = null)

    fun close()
}