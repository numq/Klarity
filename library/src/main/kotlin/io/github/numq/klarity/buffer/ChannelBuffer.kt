package io.github.numq.klarity.buffer

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException

internal class ChannelBuffer<Item>(override val capacity: Int) : Buffer<Item> {
    private var items = Channel<Item>(capacity)

    override suspend fun take() = runCatching {
        items.receive()
    }

    override suspend fun put(item: Item) = runCatching {
        items.send(item)
    }

    override suspend fun clear() = runCatching {
        runCatching {
            items.close()
        }.getOrElse { t ->
            if (t !is ClosedReceiveChannelException) {
                throw t
            }
        }

        items = Channel(capacity)
    }

    override suspend fun close() = runCatching {
        items.close()

        Unit
    }
}