package decoder

import exception.JNIException
import frame.Frame
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import media.Media

class ProbeDecoder(
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

    override fun close() = runCatching { decoder.close() }.recoverCatching(JNIException::create).getOrThrow()
}