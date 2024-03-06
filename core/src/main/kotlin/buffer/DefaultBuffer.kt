package buffer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DefaultBuffer<T> : Buffer<T> {

    private val mutex = Mutex()

    private var buffer = mutableListOf<T>()

    override var capacity = 0

    override suspend fun resize(capacity: Int) = mutex.withLock {
        this.capacity = capacity

        if (capacity < buffer.size) buffer.dropLast(buffer.size - capacity)

        Unit
    }

    override suspend fun isEmpty() = mutex.withLock { buffer.isEmpty() }

    override suspend fun isAvailable() = mutex.withLock {
        capacity > 0 && buffer.size < capacity
    }

    override suspend fun peek() = mutex.withLock {
        check(capacity > 0)

        buffer.firstOrNull()
    }

    override suspend fun poll(): T? = mutex.withLock {
        check(capacity > 0)

        buffer.removeFirstOrNull()
    }

    override suspend fun push(item: T) = mutex.withLock {
        check(capacity > 0)

        buffer.add(item)

        Unit
    }

    override suspend fun flush() = mutex.withLock {
        buffer.clear()
    }
}