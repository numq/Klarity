package io.github.numq.example.notification.queue

import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.channels.Channel
import io.github.numq.example.notification.NotificationItem

interface NotificationQueue {
    val notifications: Channel<NotificationItem>

    fun push(message: String, label: ImageVector? = null)

    fun close()
}