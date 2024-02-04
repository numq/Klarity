package theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun KlarityTheme(
    isDarkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (isDarkTheme) {
        darkColors(
            background = Color(36, 36, 47)
        )
    } else {
        lightColors(
            background = Color(36, 36, 47)
        )
    }

    MaterialTheme(
        colors = colors,
        content = content
    )
}