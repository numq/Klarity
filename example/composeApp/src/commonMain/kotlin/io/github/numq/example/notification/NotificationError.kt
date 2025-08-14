package io.github.numq.example.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.numq.example.notification.queue.NotificationQueue

@Composable
fun NotificationError(notificationQueue: NotificationQueue) {
    Notification(notificationQueue = notificationQueue) { item ->
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = item.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}