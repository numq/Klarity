package buffer

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class DefaultBuffer<T>(initialCapacity: Int) : Buffer<T> {
    private val lock = ReentrantLock()

    private var capacity: Int = initialCapacity

    private var queue = LinkedBlockingQueue<T>(capacity)

    override fun resize(newCapacity: Int) = runCatching {
        check(capacity > 0) { "Invalid buffer capacity" }

        lock.withLock {
            if (newCapacity != capacity) {
                val items = mutableListOf<T>()

                queue.drainTo(items, newCapacity)

                queue = LinkedBlockingQueue<T>(items)

                capacity = newCapacity
            }
        }
    }

    override fun poll() = runCatching {
        queue.take()
    }

    override fun push(item: T) = runCatching {
        queue.put(item)
    }

    override fun flush() = runCatching {
        queue.clear()
    }
}