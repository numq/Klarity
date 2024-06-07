package buffer

interface Buffer<T> {
    fun resize(newCapacity: Int): Result<Unit>
    fun poll(): Result<T?>
    fun push(item: T): Result<Unit>
    fun flush(): Result<Unit>

    companion object {
        internal fun <T> create(capacity: Int): Result<Buffer<T>> = runCatching {
            check(capacity > 0) { "Unable to create buffer with $capacity capacity" }

            DefaultBuffer(initialCapacity = capacity)
        }
    }
}