package theme

import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color

@Composable
fun ZeroRippleTheme(content: @Composable () -> Unit) {

    val theme = rememberSaveable("ZeroRippleTheme") {
        object : RippleTheme {
            @Composable
            override fun defaultColor() = Color.Unspecified

            @Composable
            override fun rippleAlpha() = RippleAlpha(0f, 0f, 0f, 0f)
        }
    }

    CompositionLocalProvider(LocalRippleTheme provides theme) {
        content()
    }
}