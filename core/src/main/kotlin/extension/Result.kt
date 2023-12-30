package extension

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend inline fun <reified T> Result<T>.suspend(
    noinline onFailure: ((Throwable) -> Unit)? = null,
    noinline cancellationBlock: suspend () -> Unit = {},
) = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        CoroutineScope(continuation.context).launch {
            try {
                cancellationBlock()
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }
    fold(
        onSuccess = continuation::resume,
        onFailure = onFailure ?: continuation::resumeWithException
    )
}
