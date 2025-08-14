package io.github.numq.example.item.presentation

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.numq.example.item.Item

@Composable
fun LoadingItem(
    modifier: Modifier,
    item: Item.Loading,
    remove: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition()

    val backgroundColor by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.surface,
        targetValue = MaterialTheme.colorScheme.surfaceTint,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = modifier.padding(8.dp), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors().copy(contentColor = backgroundColor)) {
            Column(
                modifier = Modifier.fillMaxSize().aspectRatio(1f).pointerInput(Unit) {
                    detectTapGestures(onLongPress = { remove() })
                }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.weight(1f))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    Text(
                        text = item.location,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}