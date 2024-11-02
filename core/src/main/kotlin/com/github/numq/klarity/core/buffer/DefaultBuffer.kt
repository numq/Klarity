package com.github.numq.klarity.core.buffer

import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class DefaultBuffer<T>(initialCapacity: Int) : Buffer<T> {
    override var capacity: Int = initialCapacity
        private set

    private var items = LinkedBlockingQueue<T>(capacity)

    override suspend fun resize(newCapacity: Int) = runCatching {
        require(newCapacity > 0) { "Invalid buffer capacity" }

        if (newCapacity != capacity) {
            capacity = newCapacity
            if (items.size > capacity) {
                items.removeAll { items.indexOf(it) > capacity - 1 }
            }
        }
    }

    override suspend fun peek() = runCatching { items.peek() }

    override suspend fun poll() = runCatching {
        suspendCancellableCoroutine { continuation ->
            val thread = thread {
                try {
                    continuation.resume(items.take())
                } catch (_: InterruptedException) {

                } catch (t: Throwable) {
                    continuation.resumeWithException(t)
                }
            }
            continuation.invokeOnCancellation {
                thread.interrupt()
            }
        }
    }

    override suspend fun push(item: T) = runCatching {
        suspendCancellableCoroutine { continuation ->
            val thread = thread {
                try {
                    continuation.resume(items.put(item))
                } catch (_: InterruptedException) {

                } catch (t: Throwable) {
                    continuation.resumeWithException(t)
                }
            }
            continuation.invokeOnCancellation {
                thread.interrupt()
            }
        }
    }

    override suspend fun flush() = runCatching {
        items.clear()
    }
}