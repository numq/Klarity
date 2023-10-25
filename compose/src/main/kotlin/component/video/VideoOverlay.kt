package component.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import player.PlaybackStatus

@Composable
fun VideoOverlay(
    status: PlaybackStatus,
    toggleable: Boolean,
    visibilityDelay: Long,
    interactionSources: List<MutableInteractionSource> = emptyList(),
    topPanel: @Composable ColumnScope.() -> Unit = {},
    midPanel: @Composable ColumnScope.() -> Unit = {},
    bottomPanel: @Composable ColumnScope.() -> Unit = {},
    content: @Composable () -> Unit = {},
) {

    val interaction by interactionSources
        .map { it.interactions }
        .merge()
        .collectAsState(null)

    val canBeHidden by remember(status, visibilityDelay) {
        derivedStateOf {
            status == PlaybackStatus.PLAYING && visibilityDelay > 0L
        }
    }

    val interactionScope = rememberCoroutineScope()

    val (interactionJob, setInteractionJob) = remember { mutableStateOf<Job?>(null) }

    val (interactionVisibility, setInteractionVisibility) = remember { mutableStateOf(false) }

    fun hide() {
        interactionJob?.cancel()
        interactionScope.launch {
            delay(visibilityDelay)
            setInteractionVisibility(false)
        }.also(setInteractionJob)
    }

    DisposableEffect(interaction) {
        interactionJob?.cancel()
        onDispose(::hide)
    }

    val isVisible by remember(canBeHidden, interactionVisibility) {
        derivedStateOf {
            !canBeHidden || (canBeHidden && interactionVisibility)
        }
    }

    Box(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = canBeHidden,
            onClick = {
                setInteractionVisibility(if (toggleable) !interactionVisibility else true)
                hide()
            }
        ), contentAlignment = Alignment.Center
    ) {
        content()
        AnimatedVisibility(isVisible, enter = fadeIn(), exit = fadeOut()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                topPanel()
                midPanel()
                bottomPanel()
            }
        }
    }
}