package com.github.numq.klarity.core.loop.buffer

data class BufferLoopException(override val cause: Throwable) : Exception(cause)