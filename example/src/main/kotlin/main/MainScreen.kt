package main

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import component.MediaPlayer
import format.MediaFormat
import extension.log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uploading.UploadingDialog

@Composable
fun MainScreen() {

    val (uploading, setUploading) = remember { mutableStateOf(false) }.log("uploading")

    val (mediaUrl, setMediaUrl) = remember { mutableStateOf("") }.log("media url")

    val (inputUrl, setInputUrl) = remember { mutableStateOf("") }.log("input url")

    val (debounceJob, setDebounceJob) = remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(inputUrl) {
        debounceJob?.cancel()
        launch {
            delay(500L)
            setMediaUrl(inputUrl)
        }.let(setDebounceJob)
    }

    val (playAudio, setPlayAudio) = remember { mutableStateOf(true) }.log("play audio")

    val (playVideo, setPlayVideo) = remember { mutableStateOf(true) }.log("play video")

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            MediaPlayer(
                mediaUrl = mediaUrl,
                playAudio = playAudio,
                playVideo = playVideo,
                loopCount = 1,
                modifier = Modifier.weight(1f)
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    inputUrl,
                    setInputUrl,
                    modifier = Modifier.padding(8.dp).weight(1f),
                    leadingIcon = {
                        IconButton(
                            onClick = {
                                setUploading(true)
                            },
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Icon(Icons.Rounded.UploadFile, "upload file")
                        }
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { setInputUrl("") },
                            enabled = inputUrl.isNotBlank(),
                            modifier = Modifier.padding(4.dp)
                        ) {
                            Icon(Icons.Rounded.Clear, "clear mediaUrl")
                        }
                    }, placeholder = {
                        Text("Upload file or type media url here")
                    })
                IconToggleButton(playAudio, setPlayAudio) {
                    Icon(Icons.Rounded.Audiotrack, "audio", tint = if (playAudio) Color.Green else Color.Red)
                }
                IconToggleButton(playVideo, setPlayVideo) {
                    Icon(Icons.Rounded.Movie, "video", tint = if (playVideo) Color.Green else Color.Red)
                }
            }
        }

        if (uploading) UploadingDialog(
            MediaFormat.audio
                .plus(MediaFormat.video)
                .map(String::lowercase)
        ) { url ->
            url.takeIf { it.isNotBlank() }?.let(setInputUrl)
            setUploading(false)
        }
    }
}