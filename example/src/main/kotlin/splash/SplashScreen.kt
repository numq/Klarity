package splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.unit.Density
import java.io.File

@Composable
fun SplashScreen(onEnd: () -> Unit) {

    val logoAnimBase = rememberSaveable {
        loadSvgPainter(File("media/logo_anim_base.svg").inputStream(), Density(1f))
    }

    val logoAnimDetail = rememberSaveable {
        loadSvgPainter(File("media/logo_anim_detail.svg").inputStream(), Density(1f))
    }

    val animatedRotation = rememberSaveable { Animatable(0f) }

    LaunchedEffect(Unit) {
        animatedRotation.animateTo(
            animationSpec = tween(360, easing = FastOutSlowInEasing),
            targetValue = 180f
        )

        onEnd()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
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