package buffer

import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.coroutines.resume

internal class DefaultBuffer<T>(
    override var capacity: Int,
) : Buffer<T> {
    private val lock = ReentrantLock()

    private var queue = LinkedBlockingQueue<T>(capacity)

    override suspend fun resize(newCapacity: Int) = runCatching {
        require(capacity > 0) { "Invalid buffer capacity" }

        val items = mutableListOf<T>()

        if (newCapacity != capacity) {
            queue.drainTo(items, newCapacity)

            lock.withLock {
                queue = LinkedBlockingQueue<T>(items)

                capacity = newCapacity
            }
        }
    }

    override suspend fun peek() = runCatching { queue.peek() }

    override suspend fun poll() = runCatching {
        suspendCancellableCoroutine { continuation ->
            val thread = thread(start = false) {
                try {
                    continuation.resume(queue.take())
                } catch (_: InterruptedException) {
                }
            }
            continuation.invokeOnCancellation {
                thread.interrupt()
            }
            thread.start()
        }
    }

    override suspend fun push(item: T) = runCatching {
        suspendCancellableCoroutine { continuation ->
            val thread = thread(start = false) {
                try {
                    continuation.resume(queue.put(item))
                } catch (_: InterruptedException) {
                }
            }
            continuation.invokeOnCancellation {
                thread.interrupt()
            }
            thread.start()
        }
    }

    override suspend fun flush() = runCatching {
        queue.clear()
    }
}