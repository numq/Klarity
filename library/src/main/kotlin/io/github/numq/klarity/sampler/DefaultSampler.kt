package io.github.numq.klarity.sampler

import io.github.numq.klarity.frame.Frame
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DefaultSampler(
    private val sampler: NativeSampler,
) : Sampler {
    private val mutex = Mutex()

    private var latency = 0L

    override suspend fun getLatency() = mutex.withLock { Result.success(latency) }

    override suspend fun start() = mutex.withLock {
        sampler.start().map {
            latency = it
        }
    }

    override suspend fun write(frame: Frame.Content.Audio, volume: Float, playbackSpeedFactor: Float) = mutex.withLock {
        sampler.write(bytes = frame.bytes, volume = volume, playbackSpeedFactor = playbackSpeedFactor)
    }

    override suspend fun stop() = mutex.withLock {
        sampler.stop()
    }

    override suspend fun flush() = mutex.withLock {
        sampler.flush()
    }

    override suspend fun drain(volume: Float, playbackSpeedFactor: Float) = mutex.withLock {
        sampler.drain(volume = volume, playbackSpeedFactor = playbackSpeedFactor)
    }

    override suspend fun close() = mutex.withLock {
        runCatching {
            sampler.close()
        }
    }
}