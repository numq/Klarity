package com.github.numq.klarity.core.sampler

import com.github.numq.klarity.core.exception.NativeException
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
        }.recoverCatching(NativeException::create)
    }

    override suspend fun setVolume(value: Float) = mutex.withLock {
        runCatching {
            require(value in 0.0f..1.0f) { "Volume should be a value between 0.0f and 1.0f" }

            sampler.setVolume(value)

            currentVolume = value
        }.recoverCatching(NativeException::create)
    }

    override suspend fun setMuted(state: Boolean) = mutex.withLock {
        runCatching {
            sampler.setVolume(if (state) 0f else currentVolume)
        }.recoverCatching(NativeException::create)
    }

    override suspend fun start() = mutex.withLock {
        runCatching {
            latency = sampler.start()
        }.recoverCatching(NativeException::create)
    }

    override suspend fun play(bytes: ByteArray) = mutex.withLock {
        runCatching {
            sampler.play(bytes, bytes.size)
        }.recoverCatching(NativeException::create)
    }

    override suspend fun pause() = mutex.withLock {
        runCatching {
            sampler.pause()
        }.recoverCatching(NativeException::create)
    }

    override suspend fun stop() = mutex.withLock {
        runCatching {
            sampler.stop()
        }.recoverCatching(NativeException::create)
    }

    override fun close() = runCatching { sampler.close() }.recoverCatching(NativeException::create).getOrThrow()
}