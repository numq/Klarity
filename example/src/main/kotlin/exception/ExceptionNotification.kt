package exception

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
import androidx.compose.material.icons.rounded.Error
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ExceptionNotification(exceptions: Channel<Exception>) {

    val coroutineScope = rememberCoroutineScope()

    val (exception, setException) = rememberSaveable { mutableStateOf<Exception?>(null) }

    val (exceptionVisible, setExceptionVisible) = rememberSaveable { mutableStateOf(false) }

    val (animationDelayJob, setAnimationDelayJob) = rememberSaveable { mutableStateOf<Job?>(null) }

    val exceptionVisibilityDelayMillis = 3000

    val slideAnimationDurationMillis = 250

    val slideAnimationSpec = rememberSaveable<FiniteAnimationSpec<IntOffset>> {
        tween(durationMillis = slideAnimationDurationMillis, easing = LinearEasing)
    }

    fun hide() {
        coroutineScope.launch {
            animationDelayJob?.cancelAndJoin()
            setAnimationDelayJob(null)
        }
    }

    LaunchedEffect(Unit) {
        exceptions.receiveAsFlow().distinctUntilChangedBy(Exception::cause).collect { e ->
            setException(e)

            setExceptionVisible(true)

            launch { delay(exceptionVisibilityDelayMillis.milliseconds) }.also(setAnimationDelayJob).join()

            setExceptionVisible(false)

            delay(slideAnimationDurationMillis.milliseconds)
        }
    }

    AnimatedVisibility(
        visible = exceptionVisible,
        enter = slideInVertically(animationSpec = slideAnimationSpec) { it },
        exit = slideOutVertically(animationSpec = slideAnimationSpec) { it }
    ) {
        DisposableEffect(Unit) {
            onDispose {
                setException(null)
            }
        }
        exception?.run {
            Card(
                Modifier.clickable { hide() },
                shape = MaterialTheme.shapes.medium.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(localizedMessage ?: toString(), modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.Error, "error")
                }
            }
        }
    }
}