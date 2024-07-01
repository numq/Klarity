package component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import extension.formatTimestamp

@Composable
fun TimeIndicator(
    playbackTimestampMillis: Long,
    durationTimestampMillis: Long,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(playbackTimestampMillis.formatTimestamp(), color = textColor)
        Text("/", color = textColor)
        Text(durationTimestampMillis.formatTimestamp(), color = textColor)
    }
}