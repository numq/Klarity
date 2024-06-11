package buffer

interface Buffer<T> {
    suspend fun resize(newCapacity: Int): Result<Unit>
    suspend fun poll(): Result<T?>
    suspend fun push(item: T): Result<Unit>
    suspend fun flush(): Result<Unit>

    companion object {
        internal fun <T> create(capacity: Int): Result<Buffer<T>> = runCatching {
            check(capacity > 0) { "Unable to create buffer with $capacity capacity" }

            DefaultBuffer(capacity = capacity)
        }
    }
}