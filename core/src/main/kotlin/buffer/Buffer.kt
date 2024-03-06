package buffer

interface Buffer<T> {
    companion object {
        fun <T> create(): Buffer<T> = DefaultBuffer()
    }

    val capacity: Int

    suspend fun resize(capacity: Int)

    suspend fun isEmpty(): Boolean

    suspend fun isAvailable(): Boolean

    suspend fun peek(): T?

    suspend fun poll(): T?

    suspend fun push(item: T)

    suspend fun flush()
}