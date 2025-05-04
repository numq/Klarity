package com.github.numq.klarity.core.buffer

interface Buffer<T> {
    val capacity: Int

    suspend fun take(): Result<T>

    suspend fun put(item: T): Result<Unit>

    suspend fun clear(): Result<Unit>

    suspend fun close(): Result<Unit>

    companion object {
        internal fun <T> create(capacity: Int): Result<Buffer<T>> = runCatching {
            require(capacity > 0) { "Unable to create buffer with $capacity capacity" }

            ChannelBuffer(capacity = capacity)
        }
    }
}