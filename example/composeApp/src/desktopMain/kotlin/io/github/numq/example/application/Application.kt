package io.github.numq.example.application

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import io.github.numq.example.decoration.WindowDecoration
import io.github.numq.example.decoration.WindowDecorationColors
import io.github.numq.example.di.appModule
import io.github.numq.example.navigation.NavigationView
import io.github.numq.example.theme.KlarityTheme
import io.github.numq.klarity.player.KlarityPlayer
import org.jetbrains.compose.resources.decodeToSvgPainter
import org.koin.compose.koinInject
import org.koin.core.context.startKoin
import java.io.File

private const val APP_NAME = "Klarity"

private val minimumWindowSize = DpSize(900.dp, 600.dp)

fun main() {
    KlarityPlayer.load().getOrThrow()

    startKoin { modules(appModule) }

    application {
        val iconSvg = remember { File("../../media/logo.svg").readBytes().decodeToSvgPainter(Density(1f)) }

        val isSystemInDarkTheme = isSystemInDarkTheme()

        val (isDarkTheme, setIsDarkTheme) = remember(isSystemInDarkTheme) {
            mutableStateOf(isSystemInDarkTheme)
        }

        KlarityTheme(isDarkTheme = isDarkTheme) {
            WindowDecoration(
                isDarkTheme = isDarkTheme,
                setIsDarkTheme = setIsDarkTheme,
                initialWindowSize = minimumWindowSize,
                minimumWindowSize = minimumWindowSize,
                windowDecorationColors = WindowDecorationColors(switchSchemeButton = { Color.Unspecified }),
                title = {
                    Box(modifier = Modifier.padding(4.dp), contentAlignment = Alignment.Center) {
                        Image(iconSvg, "icon")
                    }
                    Text(APP_NAME, color = MaterialTheme.colorScheme.primary)
                },
                content = {
                    DisposableEffect(Unit) {
                        iconImage = iconSvg.toAwtImage(Density(1f), LayoutDirection.Ltr)
                        onDispose { }
                    }

                    NavigationView(feature = koinInject())
                })
        }
    }
}