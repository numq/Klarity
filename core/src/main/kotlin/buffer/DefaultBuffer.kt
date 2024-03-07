package buffer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DefaultBuffer<T> : Buffer<T> {

    private val mutex = Mutex()

    override val list: MutableList<T> = mutableListOf()

    override var capacity = 0

    override suspend fun resize(capacity: Int) = mutex.withLock {
        this.capacity = capacity

        if (capacity < list.size) list.dropLast(list.size - capacity)

        Unit
    }

    override suspend fun isEmpty() = mutex.withLock { list.isEmpty() }

    override suspend fun isAvailable() = mutex.withLock {
        capacity > 0 && list.size < capacity
    }

    override suspend fun peek() = mutex.withLock {
        check(capacity > 0)

        list.firstOrNull()
    }

    override suspend fun poll(): T? = mutex.withLock {
        check(capacity > 0)

        list.removeFirstOrNull()
    }

    override suspend fun push(item: T) = mutex.withLock {
        check(capacity > 0)

        list.add(item)

        Unit
    }

    override suspend fun flush() = mutex.withLock {
        list.clear()
    }
}