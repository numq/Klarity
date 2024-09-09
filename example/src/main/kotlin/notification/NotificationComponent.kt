package notification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun NotificationComponent(notifications: Channel<Notification>) {
    val coroutineScope = rememberCoroutineScope()

    val (notification, setNotification) = remember { mutableStateOf<Notification?>(null) }

    val (notificationVisible, setNotificationVisible) = remember { mutableStateOf(false) }

    val (animationDelayJob, setAnimationDelayJob) = remember { mutableStateOf<Job?>(null) }

    val notificationVisibilityDelayMillis = 3000

    val slideAnimationDurationMillis = 250

    val slideAnimationSpec = remember<FiniteAnimationSpec<IntOffset>> {
        tween(durationMillis = slideAnimationDurationMillis, easing = LinearEasing)
    }

    LaunchedEffect(Unit) {
        notifications.consumeEach { notification ->
            if (notificationVisible) {
                setNotificationVisible(false)

                delay(slideAnimationDurationMillis.milliseconds)
            }

            setNotification(notification)

            setNotificationVisible(true)

            launch { delay(notificationVisibilityDelayMillis.milliseconds) }.also(setAnimationDelayJob).join()

            setNotificationVisible(false)

            delay(slideAnimationDurationMillis.milliseconds)
        }
    }

    AnimatedVisibility(
        visible = notificationVisible,
        enter = slideInVertically(animationSpec = slideAnimationSpec) { it },
        exit = slideOutVertically(animationSpec = slideAnimationSpec) { it }
    ) {
        DisposableEffect(Unit) {
            onDispose {
                setNotification(null)
            }
        }
        notification?.run {
            Card(
                Modifier.clickable {
                    coroutineScope.launch {
                        animationDelayJob?.cancelAndJoin()
                        setAnimationDelayJob(null)
                    }
                },
                shape = MaterialTheme.shapes.medium.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Error, "error")
                }
            }
        }
    }
}