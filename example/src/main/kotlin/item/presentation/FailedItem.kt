package item.presentation

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import item.Item

@Composable
fun FailedItem(
    item: Item.Failed,
    remove: () -> Unit,
) {
    Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
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
                    Icon(Icons.Default.Error, null)
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    Text(
                        text = item.throwable.message ?: "Unknown error",
                        modifier = Modifier.padding(horizontal = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}