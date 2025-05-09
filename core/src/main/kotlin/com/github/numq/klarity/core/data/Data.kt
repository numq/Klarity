package com.github.numq.klarity.core.data

import java.io.Closeable

data class Data(
    val buffer: Long,
    val capacity: Int,
    val isClosed: () -> Boolean,
    val close: () -> Unit
) : Closeable {
    companion object {
        val Empty = Data(-1L, 0, { true }, {})

        fun allocate(capacity: Int) = with(NativeData.allocate(capacity)) {
            Data(
                buffer = getBuffer(),
                capacity = capacity,
                isClosed = ::isClosed,
                close = ::close
            )
        }
    }

    fun isClosed() = isClosed.invoke()

    override fun close() = close.invoke()
}