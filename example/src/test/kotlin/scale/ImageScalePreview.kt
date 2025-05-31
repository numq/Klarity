package scale

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import io.github.numq.klarity.renderer.compose.ImageScale
import org.jetbrains.skia.Bitmap
import java.io.File
import java.net.URI

fun main() = singleWindowApplication { BuildImageScalePreview() }

@OptIn(ExperimentalComposeUiApi::class)
@Preview
@Composable
fun BuildImageScalePreview() {
    var images by remember { mutableStateOf<List<ImageBitmap>?>(null) }

    val tileSize = 64

    fun generateChessBoardPixels(width: Int, height: Int, tileSize: Int): ByteArray {
        val pixels = ByteArray(width * height * 4)

        for (h in 0 until height) {
            for (w in 0 until width) {
                val index = (h * width + w) * 4

                val isWhite = (w / tileSize + h / tileSize) % 2 == 0

                val color = if (isWhite) Color.White else Color.Black

                val argb = color.toArgb()

                pixels[index] = (argb and 0xFF).toByte()
                pixels[index + 1] = ((argb shr 8) and 0xFF).toByte()
                pixels[index + 2] = ((argb shr 16) and 0xFF).toByte()
                pixels[index + 3] = ((argb shr 24) and 0xFF).toByte()
            }
        }
        return pixels
    }

    fun generateChessboardImageBitmap(width: Int, height: Int, tileSize: Int) = Bitmap().apply {
        allocN32Pixels(width, height)
        installPixels(generateChessBoardPixels(width, height, tileSize))
    }.asComposeImageBitmap()

    val squareImage = generateChessboardImageBitmap(512, 512, tileSize)

    val horizontalImage = generateChessboardImageBitmap(1024, 512, tileSize)

    val verticalImage = generateChessboardImageBitmap(512, 1024, tileSize)

    LaunchedEffect(images) {
        if (images == null) {
            images = listOf(squareImage, horizontalImage, verticalImage)
        }
    }

    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                images = when (val data = event.dragData()) {
                    is DragData.FilesList -> data.readFiles().map { file ->
                        File(URI(file)).inputStream().use { loadImageBitmap((it)) }
                    }

                    else -> emptyList()
                }

                return true
            }
        }
    }

    Scaffold(modifier = Modifier.dragAndDropTarget(shouldStartDragAndDrop = { event ->
        event.dragData() !is DragData.Image
    }, target = dropTarget), topBar = {
        TopAppBar(modifier = Modifier.fillMaxWidth(), title = {
            Text("Image scale preview")
        }, navigationIcon = {
            IconButton(onClick = {
                images = null
            }) {
                Icon(Icons.Default.ClearAll, null)
            }
        })
    }) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            images?.let { ImageScaleMultiplePreview(it) }
        }
    }
}

@Composable
fun ImageScaleMultiplePreview(images: List<ImageBitmap>) {
    val scaleModes = remember {
        mutableStateListOf(
            ImageScale.None, ImageScale.Crop, ImageScale.Fit, ImageScale.Fill, ImageScale.FitWidth, ImageScale.FitHeight
        )
    }

    val dstRatios = remember {
        val (square, horizontal, vertical) = Triple(1f, 2f, .5f)
        arrayOf(square, horizontal, vertical)
    }

    val (chunkSize, setChunkSize) = remember {
        mutableStateOf(1)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            scaleModes.chunked(chunkSize).forEach { chunk ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    chunk.forEachIndexed { index, imageScale ->
                        Text(imageScale.javaClass.simpleName.foldIndexed("") { idx, acc, c -> acc + if (idx != 0 && c.isUpperCase()) "_${c.uppercase()}" else c.uppercase() })
                        images.forEach { image ->
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                dstRatios.forEach { dstRatio ->
                                    Surface(Modifier.weight(1f, fill = false)) {
                                        Canvas(
                                            Modifier.weight(1f).aspectRatio(dstRatio).background(Color.Green)
                                        ) {
                                            drawImage(
                                                image, dstSize = imageScale.scale(
                                                    Size(image.width.toFloat(), image.height.toFloat()), size
                                                ).let { (w, h) -> IntSize(w.toInt(), h.toInt()) })
                                        }
                                    }
                                }
                            }
                        }
                        if (index != chunk.lastIndex) Divider()
                    }
                }
                Divider(modifier = Modifier.width(1.dp).fillMaxHeight())
            }
        }
        Box(modifier = Modifier.fillMaxWidth().padding(4.dp), contentAlignment = Alignment.Center) {
            val (sliderValue, setSliderValue) = remember {
                mutableStateOf(chunkSize.toFloat())
            }

            LaunchedEffect(sliderValue) {
                setChunkSize(sliderValue.toInt())
            }

            Slider(
                sliderValue,
                onValueChange = setSliderValue,
                valueRange = (1f..3f),
                steps = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}