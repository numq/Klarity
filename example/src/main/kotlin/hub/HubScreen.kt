package hub

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.github.numq.klarity.core.player.KlarityPlayer
import com.github.numq.klarity.core.probe.ProbeManager
import com.github.numq.klarity.core.settings.VideoSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import notification.Notification
import remote.RemoteUploadingDialog
import slider.StepSlider
import java.io.File


@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun HubScreen(
    openFileChooser: () -> List<File>,
    upload: (List<String>) -> List<File>,
    notify: (Notification) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    var uploadingJob by remember { mutableStateOf<Job?>(null) }

    val pendingChannel = remember { Channel<HubItem.Pending>(Channel.UNLIMITED) }

    var hubItems by remember { mutableStateOf(emptyList<HubItem>()) }

    var isDragAndDrop by remember { mutableStateOf(false) }

    var isRemoteUploadingDialogVisible by remember { mutableStateOf(false) }

    DisposableEffect(pendingChannel) {
        uploadingJob = pendingChannel.consumeAsFlow().onEach { pendingItem ->
            hubItems += pendingItem
            coroutineScope.launch(Dispatchers.Default) {
                ProbeManager.probe(pendingItem.location).mapCatching { media ->
                    val uploadedItem = HubItem.Uploaded(
                        player = KlarityPlayer.create().getOrThrow().apply {
                            prepare(
                                location = media.location,
                                enableAudio = true,
                                enableVideo = true,
                                videoSettings = VideoSettings(loadPreview = true)
                            ).getOrDefault(Unit)
                        }
                    )
                    hubItems = hubItems.map { if (it.id == pendingItem.id) uploadedItem else it }
                }.onFailure {
                    hubItems -= pendingItem
                }
            }
        }.launchIn(coroutineScope)

        onDispose {
            uploadingJob?.cancel()
            uploadingJob = null
            pendingChannel.close()
        }
    }

    fun addLocation(location: String) {
        val pendingItem = HubItem.Pending(location = location)
        coroutineScope.launch {
            pendingChannel.send(pendingItem)
        }
    }

    fun addFiles(files: List<File>) {
        files.map(File::getAbsolutePath).forEach(::addLocation)
    }

    fun deleteHubItem(hubItem: HubItem) {
        if (hubItem is HubItem.Pending) {
            hubItems -= hubItem
        } else if (hubItem is HubItem.Uploaded) {
            hubItems = hubItems.filterNot { item -> item is HubItem.Uploaded && item.id == hubItem.id }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().onExternalDrag(onDragStart = {
            isDragAndDrop = true
        }, onDragExit = {
            isDragAndDrop = false
        }, onDrop = { externalDragValue ->
            isDragAndDrop = false
            when (val data = externalDragValue.dragData) {
                is DragData.FilesList -> addFiles(upload(data.readFiles()))
                is DragData.Text -> addLocation(data.readText())
                else -> Unit
            }
        }), contentAlignment = Alignment.Center
    ) {
        val sliderSteps = 5

        var sliderStep by remember { mutableStateOf(0) }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TopAppBar(title = {
                if (hubItems.isNotEmpty()) {
                    Text(
                        text = "Files: ${hubItems.count()}", modifier = Modifier.padding(8.dp), maxLines = 1
                    )
                }
            }, actions = {
                IconButton(onClick = {
                    isRemoteUploadingDialogVisible = true
                }) {
                    Icon(
                        if (isRemoteUploadingDialogVisible) Icons.Outlined.Public else Icons.Default.Public, null
                    )
                }
                IconButton(onClick = {
                    addFiles(openFileChooser())
                }) {
                    Icon(Icons.Rounded.UploadFile, null)
                }
            })

            LazyVerticalGrid(
                columns = GridCells.Fixed(sliderStep + 2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.Top,
            ) {
                items(hubItems, key = (HubItem::id)) { hubItem ->
                    Box(
                        modifier = Modifier.fillMaxSize().animateItemPlacement(
                            animationSpec = tween(easing = LinearEasing)
                        ), contentAlignment = Alignment.Center
                    ) {
                        when (hubItem) {
                            is HubItem.Pending -> PendingHubItem(
                                hubItem = hubItem,
                                delete = { deleteHubItem(hubItem) }
                            )

                            is HubItem.Uploaded -> UploadedHubItem(
                                hubItem = hubItem,
                                delete = { deleteHubItem(hubItem) },
                                notify = notify
                            )
                        }
                    }
                }
            }

            Divider(modifier = Modifier.fillMaxWidth())

            val sliderColor by ButtonDefaults.buttonColors().contentColor(true)

            val pointColor by ButtonDefaults.buttonColors().backgroundColor(true)

            StepSlider(initialStep = sliderStep,
                steps = sliderSteps,
                sliderColor = sliderColor,
                pointColor = pointColor,
                onValueChange = { step ->
                    sliderStep = step
                })
        }

        if (isDragAndDrop) {
            LaunchedEffect(Unit) {
                isRemoteUploadingDialogVisible = false
            }

            Box(
                modifier = Modifier.fillMaxSize().background(color = MaterialTheme.colors.primaryVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Upload,
                    null,
                    modifier = Modifier.fillMaxSize(.25f),
                    tint = MaterialTheme.colors.primary
                )
            }
        } else {
            RemoteUploadingDialog(isVisible = isRemoteUploadingDialogVisible, done = { address ->
                addLocation(address)
                isRemoteUploadingDialogVisible = false
            }, close = {
                isRemoteUploadingDialogVisible = false
            })
        }
    }
}