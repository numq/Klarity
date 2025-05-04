package com.github.numq.klarity.core.data

import java.io.Closeable

data class Data(
    val pointer: Long,
    val capacity: Int,
    val isClosed: () -> Boolean,
    val close: () -> Unit
) : Closeable {
    companion object {
        fun allocate(capacity: Int) = with(NativeData.allocate(capacity)) {
            Data(
                pointer = pointer,
                capacity = capacity,
                isClosed = ::isClosed,
                close = ::close
            )
        }
    }

    fun isClosed() = isClosed.invoke()

    override fun close() = close.invoke()
}