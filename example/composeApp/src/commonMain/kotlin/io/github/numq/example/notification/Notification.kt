package io.github.numq.example.notification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.numq.example.notification.queue.NotificationQueue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Notification(
    notificationQueue: NotificationQueue,
    content: @Composable (NotificationItem) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    val (notification, setNotification) = remember { mutableStateOf<NotificationItem?>(null) }

    val (delayJob, setDelayJob) = remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        setNotification(notificationQueue.notifications.receive())

        launch { delay(5_000L) }
            .also(setDelayJob)
            .apply { invokeOnCompletion { setDelayJob(null) } }
            .join()

        setNotification(null)
    }

    AnimatedVisibility(
        visible = notification != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = Modifier.clickable {
            delayJob?.cancel()

            setDelayJob(null)

            setNotification(null)
        }
    ) {
        DisposableEffect(Unit) {
            onDispose {
                coroutineScope.launch {
                    setNotification(notificationQueue.notifications.receive())

                    launch { delay(2_000L) }
                        .also(setDelayJob)
                        .apply { invokeOnCompletion { setDelayJob(null) } }
                        .join()

                    setNotification(null)
                }
            }
        }

        val visibleNotification = remember { notification }

        visibleNotification?.let { content(it) }
    }
}