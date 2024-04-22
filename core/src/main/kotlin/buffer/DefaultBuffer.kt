package buffer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DefaultBuffer<T>(override var capacity: Int = 1) : Buffer<T> {

    private val mutex = Mutex()

    override val list: MutableList<T> = mutableListOf()

    override suspend fun resize(capacity: Int) = mutex.withLock {
        check(capacity > 0) { "Invalid buffer capacity" }

        this.capacity = capacity

        if (capacity < list.size) list.dropLast(list.size - capacity)

        Unit
    }

    override suspend fun isEmpty() = mutex.withLock { list.isEmpty() }

    override suspend fun isAvailable() = mutex.withLock { list.size < capacity }

    override suspend fun peek() = mutex.withLock {
        list.firstOrNull()
    }

    override suspend fun poll(): T? = mutex.withLock {
        list.removeFirstOrNull()
    }

    override suspend fun push(item: T) = mutex.withLock {
        check(capacity > 0) { "Invalid buffer capacity" }

        list.add(item)

        Unit
    }

    override suspend fun flush() = mutex.withLock {
        list.clear()
    }
}