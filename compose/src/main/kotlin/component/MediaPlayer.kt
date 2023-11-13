package component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import component.video.VideoRenderer
import extension.formatTimestamp
import kotlinx.coroutines.launch
import player.PlaybackStatus
import player.PlayerController
import player.PlayerState
import kotlin.time.Duration.Companion.nanoseconds

@Composable
fun MediaPlayer(
    mediaUrl: String?,
    loopCount: Int,
    blurredBackground: Boolean,
    showTimestamp: Boolean = true,
    showVolume: Boolean = true,
    bufferDurationSeconds: Int? = null,
    modifier: Modifier = Modifier,
    onError: (Exception) -> Unit = {},
    onState: (PlayerState) -> Unit = {},
    onStatus: (PlaybackStatus) -> Unit = {},
    placeholder: @Composable () -> Unit = {},
) {

    val coroutineScope = rememberCoroutineScope()

    val player = remember(bufferDurationSeconds) {
        PlayerController.create(bufferDurationSeconds)
    }

    DisposableEffect(Unit) {
        onDispose { player.close() }
    }

    val error by player.error.collectAsState(null)
    val state by player.state.collectAsState()
    val status by player.status.collectAsState()

    LaunchedEffect(error) { error?.let { onError(it) } }
    LaunchedEffect(state) { onState(state) }
    LaunchedEffect(status) { onStatus(status) }

    LaunchedEffect(mediaUrl) {
        mediaUrl?.takeIf { it.isNotBlank() }?.let { url ->
            player.load(
                mediaUrl = url,
                loopCount = loopCount
            )
        }
    }

    state.media?.run {

        val (contentOffset, setContentOffset) = remember { mutableStateOf(DpOffset.Zero) }

        val (contentSize, setContentSize) = remember { mutableStateOf(DpSize.Zero) }

        val (overlayVisible, setOverlayVisible) = remember { mutableStateOf(true) }

        Box(modifier.clickable { setOverlayVisible(!overlayVisible) }) {
            VideoRenderer(
                media = this@run,
                videoFrame = player.videoFrame.collectAsState().value,
                blurredBackground = blurredBackground,
                onContentOffset = setContentOffset,
                onContentSize = setContentSize,
                modifier = Modifier.fillMaxSize()
            )

            AnimatedVisibility(
                overlayVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .size(contentSize.width, contentSize.height)
                    .offset(contentOffset.x, contentOffset.y)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .5f)).padding(8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(name ?: url)
                    }
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = status == PlaybackStatus.PLAYING,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    player.pause()
                                }
                            }) {
                                Icon(
                                    Icons.Rounded.PauseCircle, "pause", modifier = Modifier.size(64.dp)
                                )
                            }
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = status != PlaybackStatus.PLAYING,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    player.play()
                                }
                                setOverlayVisible(false)
                            }) {
                                Icon(
                                    Icons.Rounded.PlayCircle, "play", modifier = Modifier.size(64.dp)
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .5f)).padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showTimestamp) {
                            Text("${state.playbackTimestampMillis.formatTimestamp()}/${durationNanos.nanoseconds.inWholeMilliseconds.formatTimestamp()}")
                        }
                        Timeline(
                            timestampMillis = state.playbackTimestampMillis,
                            durationMillis = durationNanos.nanoseconds.inWholeMilliseconds,
                            seekTo = { timestampMillis ->
                                coroutineScope.launch {
                                    player.seekTo(timestampMillis)
                                }
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        if (showVolume) {
                            Volume(
                                volume = state.volume,
                                isMuted = state.isMuted,
                                toggleMute = {
                                    coroutineScope.launch {
                                        player.toggleMute()
                                    }
                                },
                                changeVolume = { volume ->
                                    coroutineScope.launch {
                                        player.changeVolume(volume)
                                    }
                                },
                                modifier = Modifier.weight(.3f).padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    } ?: placeholder()
}