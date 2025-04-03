package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.frame.NativeFrame
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.media.Media
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AudioDecoder(
    private val decoder: NativeDecoder,
    override val media: Media.Audio,
) : Decoder<Media.Audio, Frame.Audio> {
    private val mutex = Mutex()

    override val hardwareAcceleration =
        HardwareAcceleration.fromNative(decoder.hardwareAcceleration) ?: HardwareAcceleration.None

    override suspend fun decode(width: Int?, height: Int?) = mutex.withLock {
        runCatching {
            decoder.decode(null, null)?.run {
                when (type) {
                    NativeFrame.Type.AUDIO.ordinal -> Frame.Audio.Content(
                        timestampMicros = timestampMicros,
                        bytes = bytes,
                        channels = decoder.format.channels,
                        sampleRate = decoder.format.sampleRate
                    )

                    else -> null
                }
            } ?: Frame.Audio.EndOfStream
        }
    }

    override suspend fun seekTo(micros: Long, keyframesOnly: Boolean) = mutex.withLock {
        runCatching {
            decoder.seekTo(micros, keyframesOnly)
        }
    }

    override suspend fun reset() = mutex.withLock {
        runCatching {
            decoder.reset()
        }
    }

    override suspend fun close() = mutex.withLock {
        runCatching {
            decoder.close()
        }
    }
}