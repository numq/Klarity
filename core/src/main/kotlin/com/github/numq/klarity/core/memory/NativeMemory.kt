package com.github.numq.klarity.core.memory

internal object NativeMemory {
    @JvmStatic
    private external fun freeNative(handle: Long)

    fun free(handle: Long) = runCatching { freeNative(handle = handle) }
}