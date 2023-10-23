package component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import component.video.VideoOverlay
import component.video.VideoRenderer
import media.ImageSize
import media.MediaSettings
import player.PlaybackStatus
import player.PlayerController
import player.PlayerState
import kotlin.time.Duration.Companion.nanoseconds

typealias Width = Int
typealias Height = Int

@Composable
fun MediaPlayer(
    mediaUrl: String?,
    playAudio: Boolean,
    playVideo: Boolean,
    loopCount: Int = 1,
    size: Pair<Width, Height>? = null,
    frameRate: Double? = null,
    audioBufferSize: Int? = null,
    videoBufferSize: Int? = null,
    toggleableOverlay: Boolean = true,
    overlayVisibilityDelay: Long = 2_000L,
    modifier: Modifier = Modifier,
    placeholder: (@Composable () -> Unit)? = null,
    onState: (PlayerState) -> Unit = {},
    onStatus: (PlaybackStatus) -> Unit = {},
) {
    val player = remember(audioBufferSize, videoBufferSize) {
        PlayerController.create(audioBufferSize, videoBufferSize)
    }

    DisposableEffect(Unit) {
        onDispose { player.close() }
    }

    val settings = remember(mediaUrl, playAudio, playVideo, size, frameRate) {
        mediaUrl?.takeIf { it.isNotBlank() }?.let { url ->
            MediaSettings(
                mediaUrl = url,
                hasAudio = playAudio,
                hasVideo = playVideo,
                imageSize = size?.let { (w, h) -> ImageSize(w, h) },
                frameRate = frameRate
            )
        }
    }

    LaunchedEffect(settings) {
        settings?.let(player::load)
    }

    val state by player.state.collectAsState().apply { onState(value) }

    val status by player.status.collectAsState().apply { onStatus(value) }

    val (playsLeft, setPlaysLeft) = remember {
        mutableStateOf(loopCount)
    }

    LaunchedEffect(status == PlaybackStatus.COMPLETED) {
        setPlaysLeft((playsLeft - 1).coerceAtLeast(0))
        if (loopCount == 0 || playsLeft > 0) {
            player.play()
        }
    }

    val media by player.media.collectAsState()

    Box(modifier, contentAlignment = Alignment.Center) {
        media?.run {
            val timeline: @Composable (MutableInteractionSource) -> Unit = { interactionSource ->
                Timeline(
                    timestampMillis = state.playbackTimestampMillis,
                    durationMillis = durationNanos.nanoseconds.inWholeMilliseconds,
                    seekTo = player::seekTo,
                    interactionSource = interactionSource
                )
            }
            val controls: @Composable (MutableInteractionSource) -> Unit = { interactionSource ->
                Controls(
                    timestampMillis = state.playbackTimestampMillis,
                    durationMillis = durationNanos.nanoseconds.inWholeMilliseconds,
                    state = state,
                    status = status,
                    play = player::play,
                    pause = player::pause,
                    stop = player::stop,
                    toggleMute = player::toggleMute,
                    changeVolume = player::changeVolume,
                    volumeInteractionSource = interactionSource
                )
            }
            val audio: @Composable () -> Unit = {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    timeline(MutableInteractionSource())
                    controls(MutableInteractionSource())
                }
            }
            val video: @Composable () -> Unit = {

                val pixels by player.pixels.collectAsState()

                val volumeInteractionSource = remember { MutableInteractionSource() }

                VideoOverlay(
                    status = status,
                    toggleable = toggleableOverlay,
                    visibilityDelay = overlayVisibilityDelay,
                    interactionSources = listOf(volumeInteractionSource),
                    topPanel = {
                        Box(
                            Modifier.fillMaxWidth().background(Color.White.copy(alpha = .5f)).padding(8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(name)
                        }
                    }, bottomPanel = {
                        Column(
                            Modifier.fillMaxWidth().background(Color.White.copy(alpha = .5f)).padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            timeline(volumeInteractionSource)
                            controls(volumeInteractionSource)
                        }
                    }) {
                    VideoRenderer(
                        media = this, pixels = pixels, modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(Icons.Rounded.BrokenImage, "unable to draw pixels")
                    }
                }
            }
            when {
                settings?.hasAudio == true && settings.hasVideo -> video()
                settings?.hasVideo == true -> video()
                settings?.hasAudio == true -> audio()
            }
        } ?: placeholder ?: CircularProgressIndicator()
    }
}