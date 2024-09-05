package exception

data class JNIException(override val message: String) : Exception(message) {
    internal companion object {
        private const val PREFIX = "JNI ERROR: "

        inline fun <reified R> create(throwable: Throwable): R {
            throw throwable.message?.takeIf { message ->
                message.startsWith(PREFIX)
            }?.let { message ->
                JNIException(message.removePrefix(PREFIX))
            } ?: throwable
        }
    }
}