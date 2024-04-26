package buffer

interface Buffer<T> {
    companion object {
        fun <T> create(capacity: Int): Result<Buffer<T>> = when {
            capacity > 0 -> Result.success(DefaultBuffer(capacity = capacity))

            else -> Result.failure(Exception("Unable to create buffer with $capacity capacity"))
        }
    }

    val list: List<T>

    val capacity: Int

    suspend fun resize(capacity: Int)

    suspend fun isEmpty(): Boolean

    suspend fun isAvailable(): Boolean

    suspend fun peek(): T?

    suspend fun poll(): T?

    suspend fun push(item: T)

    suspend fun flush()
}