package navigation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

@Composable
fun NavigationAnimation(
    isActive: Boolean, enter: Float, exit: Float, onAnimationEnd: () -> Unit, content: @Composable () -> Unit
) {
    val animationSpec = remember<AnimationSpec<Float>> {
        tween(durationMillis = 500, easing = LinearEasing)
    }

    var animationState by remember {
        mutableStateOf(if (isActive) NavigationAnimationState.VISIBLE else NavigationAnimationState.HIDDEN_LEFT)
    }

    LaunchedEffect(isActive) {
        animationState = if (isActive) NavigationAnimationState.VISIBLE else NavigationAnimationState.HIDDEN_LEFT
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val animatedOffset by animateFloatAsState(
            targetValue = when (animationState) {
                NavigationAnimationState.VISIBLE -> 0f

                NavigationAnimationState.HIDDEN_LEFT -> enter

                NavigationAnimationState.HIDDEN_RIGHT -> exit
            }, animationSpec = animationSpec, finishedListener = {
                if (animationState != NavigationAnimationState.VISIBLE) {
                    onAnimationEnd()
                }
            })

        if (animationState == NavigationAnimationState.VISIBLE || abs(animatedOffset) < maxWidth.value) {
            Box(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    translationX = animatedOffset
                }) {
                content()
            }
        }
    }
}