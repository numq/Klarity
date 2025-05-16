package io.github.numq.klarity.loop.buffer

data class BufferLoopException(override val cause: Throwable) : Exception(cause)