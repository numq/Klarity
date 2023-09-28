package buffer

sealed class BufferException private constructor(override val message: String) : Exception(message) {
    object FailedToCreate : BufferException("Failed to create a buffer that has neither audio nor video.")
}