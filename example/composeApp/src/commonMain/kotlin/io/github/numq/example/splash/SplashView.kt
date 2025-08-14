package io.github.numq.example.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.Density
import org.jetbrains.compose.resources.decodeToSvgPainter
import java.io.File

@Composable
fun SplashView(endOfAnimation: () -> Unit) {
    val logoAnimBase = remember {
        File("../../media/logo_anim_base.svg").readBytes().decodeToSvgPainter(Density(1f))
    }

    val logoAnimDetail = remember {
        File("../../media/logo_anim_detail.svg").readBytes().decodeToSvgPainter(Density(1f))
    }

    val animatedRotation = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animatedRotation.animateTo(
            animationSpec = tween(360, easing = FastOutSlowInEasing),
            targetValue = 180f
        )

        endOfAnimation()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = logoAnimBase,
            contentDescription = "anim base",
            modifier = Modifier.fillMaxSize()
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(
                painter = logoAnimDetail,
                contentDescription = "anim detail",
                modifier = Modifier.rotate(animatedRotation.value)
            )
        }
    }
}