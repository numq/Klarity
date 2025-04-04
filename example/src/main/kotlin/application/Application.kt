package application

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import com.github.numq.klarity.core.player.KlarityPlayer
import decoration.DecorationBox
import navigation.Navigation
import theme.KlarityTheme
import java.awt.Dimension
import java.awt.FileDialog
import java.io.File
import kotlin.system.exitProcess

const val APP_NAME = "Klarity"

fun main() {
    val pathToBinaries = Thread.currentThread().contextClassLoader.getResource("bin")?.file

    checkNotNull(pathToBinaries) { "Binaries not found" }

    KlarityPlayer.load(
        avutil = "$pathToBinaries\\avutil-59.dll",
        postproc = "$pathToBinaries\\postproc-58.dll",
        swresample = "$pathToBinaries\\swresample-5.dll",
        swscale = "$pathToBinaries\\swscale-8.dll",
        avcodec = "$pathToBinaries\\avcodec-61.dll",
        avformat = "$pathToBinaries\\avformat-61.dll",
        avfilter = "$pathToBinaries\\avfilter-10.dll",
        avdevice = "$pathToBinaries\\avdevice-61.dll",
        portaudio = "$pathToBinaries\\portaudio.dll",
        klarity = "$pathToBinaries\\klarity.dll",
    ).getOrThrow()

    val windowState = WindowState(position = WindowPosition(Alignment.Center), size = DpSize(700.dp, 700.dp))

    singleWindowApplication(state = windowState, undecorated = true) {
        val iconSvg = remember {
            File("media/logo.svg").inputStream().use {
                loadSvgPainter(it, Density(1f))
            }
        }

        SideEffect {
            window.iconImage = iconSvg.toAwtImage(Density(1f), LayoutDirection.Ltr)

            window.minimumSize = Dimension(700, 700)
        }

        val isSystemInDarkTheme = isSystemInDarkTheme()

        val (isDarkTheme, setIsDarkTheme) = remember(isSystemInDarkTheme) {
            mutableStateOf(isSystemInDarkTheme)
        }

        KlarityTheme(isDarkTheme = isDarkTheme) {
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                DecorationBox(window = window,
                    isDarkTheme = isDarkTheme,
                    changeTheme = setIsDarkTheme,
                    close = { exitProcess(0) }) {
                    Box(modifier = Modifier.padding(4.dp), contentAlignment = Alignment.Center) {
                        Image(iconSvg, "icon")
                    }
                    Text(APP_NAME, color = MaterialTheme.colors.primary)
                }
                Navigation(openFileChooser = {
                    FileDialog(window, "Upload media", FileDialog.LOAD).apply {
                        isMultipleMode = true
                        isVisible = true
                    }.files.toList()
                })
            }
        }
    }
}