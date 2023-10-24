package extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState

@Composable
fun <T> MutableState<T>.log(message: String? = null) = apply {
    LaunchedEffect(this) {
        println(message?.let { m -> "$m: $value" } ?: value)
    }
}