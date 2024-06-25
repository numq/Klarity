package buffer

import kotlinx.coroutines.flow.Flow

interface Buffer<T> {
    val items: Flow<T>
    suspend fun resize(newCapacity: Int): Result<Unit>
    suspend fun peek(): Result<T?>
    suspend fun poll(): Result<T?>
    suspend fun push(item: T): Result<Unit>
    suspend fun flush(): Result<Unit>

    companion object {
        internal fun <T> create(
            capacity: Int,
        ): Result<Buffer<T>> = runCatching {
            require(capacity > 0) { "Unable to create buffer with $capacity capacity" }

            DefaultBuffer(capacity = capacity)
        }
    }
}