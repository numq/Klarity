package com.github.numq.klarity.core.closeable

interface SuspendAutoCloseable {
    suspend fun close()
}

suspend inline fun <T : SuspendAutoCloseable, R> T.use(block: (T) -> R): R {
    var throwable: Throwable? = null

    try {
        return block(this)
    } catch (t: Throwable) {
        throwable = t

        throw t
    } finally {
        runCatching { this.close() }.onFailure { closeException ->
            throwable?.addSuppressed(closeException) ?: throw closeException
        }
    }
}