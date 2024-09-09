package hub

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun PendingHubItem(
    hubItem: HubItem.Pending,
    delete: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition()

    val backgroundColor by infiniteTransition.animateColor(
        initialValue = Color.LightGray,
        targetValue = Color.Gray,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
        Card(backgroundColor = backgroundColor) {
            Box(
                modifier = Modifier.fillMaxSize().aspectRatio(1f).pointerInput(Unit) {
                    detectTapGestures(onLongPress = { delete() })
                }, contentAlignment = Alignment.Center
            ) {
                Text(text = hubItem.location, modifier = Modifier.padding(8.dp))
            }
        }
    }
}