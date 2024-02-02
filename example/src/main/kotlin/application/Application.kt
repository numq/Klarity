package application

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import decoration.DecorationBox
import navigation.Navigation
import splash.SplashScreen
import theme.KlarityTheme
import java.awt.Dimension
import java.io.File

fun main() = application {

    val appName = "Klarity"

    val (justLaunched, setJustLaunched) = rememberSaveable { mutableStateOf(true) }

    val iconSvg = rememberSaveable {
        loadSvgPainter(File("media/logo.svg").inputStream(), Density(1f))
    }

    val slideAnimationSpec = rememberSaveable<FiniteAnimationSpec<IntOffset>> {
        tween(delayMillis = 500, easing = LinearEasing)
    }

    val windowState = rememberWindowState(position = WindowPosition(Alignment.Center))

    Window(onCloseRequest = ::exitApplication, state = windowState, undecorated = true) {

        SideEffect {
            window.iconImage = iconSvg.toAwtImage(Density(1f), LayoutDirection.Ltr)

            window.minimumSize = Dimension(500, 500)
        }

        KlarityTheme(isDarkTheme = isSystemInDarkTheme()) {
            AnimatedVisibility(
                visible = justLaunched,
                enter = slideInHorizontally(animationSpec = slideAnimationSpec) { -it },
                exit = slideOutHorizontally(animationSpec = slideAnimationSpec) { it }
            ) {
                SplashScreen {
                    setJustLaunched(false)
                }
            }
            AnimatedVisibility(
                visible = !justLaunched,
                enter = slideInHorizontally(animationSpec = slideAnimationSpec) { -it },
                exit = slideOutHorizontally(animationSpec = slideAnimationSpec) { it }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    window.DecorationBox(exitApplication = ::exitApplication) {
                        Image(iconSvg, "icon")
                        Text(appName, color = Color.White)
                    }
                    Navigation()
                }
            }
        }
    }
}