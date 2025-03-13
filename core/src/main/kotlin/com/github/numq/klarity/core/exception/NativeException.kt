package com.github.numq.klarity.core.exception

data class NativeException(override val cause: Throwable) : Exception(cause) {
    internal companion object {
        fun <T> create(throwable: Throwable): T = throw NativeException(throwable)
    }
}