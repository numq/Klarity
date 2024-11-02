package com.github.numq.klarity.core.exception

data class NativeException(override val message: String) : Exception(message) {
    internal companion object {
        inline fun <reified R> create(throwable: Throwable): R {
            throw NativeException(throwable.message ?: "Unknown native exception occurred")
        }
    }
}