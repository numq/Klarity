package io.github.numq.klarity.sampler

import io.github.numq.klarity.frame.Frame
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DefaultSampler(
    private val sampler: NativeSampler,
) : Sampler {
    private val mutex = Mutex()

    private var latency = 0L

    private var currentVolume = 1f

    private var playbackSpeedFactor = 1f

    override suspend fun getLatency() = mutex.withLock { Result.success(latency) }

    override suspend fun setPlaybackSpeed(factor: Float) = mutex.withLock {
        sampler.setPlaybackSpeed(factor = factor).map {
            playbackSpeedFactor = factor
        }
    }

    override suspend fun setVolume(value: Float) = mutex.withLock {
        sampler.setVolume(value = value).map {
            currentVolume = value
        }
    }

    override suspend fun setMuted(state: Boolean) = mutex.withLock {
        sampler.setVolume(value = if (state) 0f else currentVolume)
    }

    override suspend fun start() = mutex.withLock {
        sampler.start().map {
            latency = it
        }
    }

    override suspend fun write(frame: Frame.Content.Audio) = mutex.withLock {
        sampler.write(bytes = frame.bytes)
    }

    override suspend fun stop() = mutex.withLock {
        sampler.stop()
    }

    override suspend fun flush() = mutex.withLock {
        sampler.flush()
    }

    override suspend fun drain() = mutex.withLock {
        sampler.drain()
    }

    override suspend fun close() = mutex.withLock {
        runCatching {
            sampler.close()
        }
    }
}