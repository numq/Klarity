package com.github.numq.klarity.core.sampler

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DefaultSampler(
    private val sampler: NativeSampler,
) : Sampler {
    private val mutex = Mutex()

    private var latency = 0L

    private var currentVolume = 1f

    override var playbackSpeedFactor = MutableStateFlow(1f)

    override suspend fun getLatency() = mutex.withLock { Result.success(latency) }

    override suspend fun setPlaybackSpeed(factor: Float) = mutex.withLock {
        runCatching {
            require(factor > 0f) { "Speed factor should be positive" }

            sampler.setPlaybackSpeed(factor)

            playbackSpeedFactor.emit(factor)
        }
    }

    override suspend fun setVolume(value: Float) = mutex.withLock {
        runCatching {
            require(value in 0.0f..1.0f) { "Volume should be a value between 0.0f and 1.0f" }

            sampler.setVolume(value)

            currentVolume = value
        }
    }

    override suspend fun setMuted(state: Boolean) = mutex.withLock {
        runCatching {
            sampler.setVolume(if (state) 0f else currentVolume)
        }
    }

    override suspend fun start() = mutex.withLock {
        runCatching {
            latency = sampler.start()
        }
    }

    override suspend fun play(bytes: ByteArray) = mutex.withLock {
        runCatching {
            sampler.play(bytes, bytes.size)
        }
    }

    override suspend fun pause() = mutex.withLock {
        runCatching {
            sampler.pause()
        }
    }

    override suspend fun stop() = mutex.withLock {
        runCatching {
            sampler.stop()
        }
    }

    override suspend fun close() = mutex.withLock {
        sampler.close()
    }
}