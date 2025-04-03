package com.github.numq.klarity.core.buffer

import kotlinx.coroutines.channels.Channel

internal class ChannelBuffer<T>(override val capacity: Int) : Buffer<T> {
    private var items = Channel<T>(capacity)

    override suspend fun poll() = runCatching { items.receive() }

    override suspend fun push(item: T) = runCatching { items.send(item) }

    override suspend fun flush() = runCatching {
        items.cancel()

        items = Channel(capacity)
    }

    override suspend fun close() = runCatching {
        items.close()

        Unit
    }
}