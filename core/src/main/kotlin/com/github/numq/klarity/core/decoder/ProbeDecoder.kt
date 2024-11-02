package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.exception.NativeException
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ProbeDecoder(
    private val decoder: NativeDecoder,
    override val media: Media,
) : Decoder<Media, Frame.Probe> {
    private val mutex = Mutex()

    override suspend fun nextFrame(width: Int?, height: Int?) = mutex.withLock {
        Result.success(Frame.Probe)
    }

    override suspend fun seekTo(micros: Long, keyframesOnly: Boolean) = mutex.withLock {
        Result.success(Unit)
    }

    override suspend fun reset() = mutex.withLock {
        Result.success(Unit)
    }

    override fun close() = runCatching { decoder.close() }.recoverCatching(NativeException::create).getOrThrow()
}