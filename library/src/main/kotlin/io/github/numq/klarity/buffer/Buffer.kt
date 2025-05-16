package io.github.numq.klarity.buffer

interface Buffer<Item> {
    val capacity: Int

    suspend fun take(): Result<Item>

    suspend fun put(item: Item): Result<Unit>

    suspend fun clear(): Result<Unit>

    suspend fun close(): Result<Unit>

    companion object {
        internal fun <Item> create(capacity: Int): Result<Buffer<Item>> = runCatching {
            require(capacity > 0) { "Unable to create buffer with $capacity capacity" }

            ChannelBuffer(capacity = capacity)
        }
    }
}