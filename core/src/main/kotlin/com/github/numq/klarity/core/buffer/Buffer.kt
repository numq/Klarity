package com.github.numq.klarity.core.buffer

interface Buffer<T> {
    val capacity: Int

    suspend fun poll(): Result<T>

    suspend fun put(item: T): Result<Unit>

    suspend fun flush(): Result<Unit>

    companion object {
        internal fun <T> create(capacity: Int): Result<Buffer<T>> = runCatching {
            require(capacity > 0) { "Unable to create buffer with $capacity capacity" }

            BlockingBuffer(capacity = capacity)
        }
    }
}