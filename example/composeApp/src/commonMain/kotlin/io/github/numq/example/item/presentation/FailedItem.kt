package io.github.numq.example.item.presentation

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.numq.example.item.Item

@Composable
fun FailedItem(
    modifier: Modifier,
    item: Item.Failed,
    remove: () -> Unit,
) {
    Box(modifier = modifier.padding(8.dp), contentAlignment = Alignment.Center) {
        Card {
            Column(
                modifier = Modifier.fillMaxSize().aspectRatio(1f).pointerInput(Unit) {
                    detectTapGestures(onLongPress = { remove() })
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.weight(1f))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.primary)
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    Text(
                        text = item.throwable.message ?: "Unknown error",
                        modifier = Modifier.padding(horizontal = 4.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}