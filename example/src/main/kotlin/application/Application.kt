package application

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import decoration.DecorationBox
import di.appModule
import io.github.numq.klarity.player.KlarityPlayer
import navigation.NavigationView
import org.koin.compose.koinInject
import org.koin.core.context.startKoin
import theme.KlarityTheme
import java.awt.Dimension
import java.io.File
import kotlin.system.exitProcess

private const val APP_NAME = "Klarity"

private val windowSize = DpSize(512.dp, 512.dp)

private val windowState = WindowState(position = WindowPosition(Alignment.Center), size = windowSize)

fun main() {
    KlarityPlayer.load().getOrThrow()

    startKoin { modules(appModule) }

    singleWindowApplication(state = windowState, undecorated = true) {
        val iconSvg = File("media/logo.svg").inputStream().use {
            loadSvgPainter(it, Density(1f))
        }

        window.iconImage = iconSvg.toAwtImage(Density(1f), LayoutDirection.Ltr)

        window.minimumSize = Dimension(windowSize.width.value.toInt(), windowSize.height.value.toInt())

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
                DecorationBox(
                    window = window,
                    isDarkTheme = isDarkTheme,
                    changeTheme = setIsDarkTheme,
                    close = { exitProcess(0) }) {
                    Box(modifier = Modifier.padding(4.dp), contentAlignment = Alignment.Center) {
                        Image(iconSvg, "icon")
                    }
                    Text(APP_NAME, color = MaterialTheme.colors.primary)
                }

                NavigationView(feature = koinInject())
            }
        }
    }
}