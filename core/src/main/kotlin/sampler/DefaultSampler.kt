package sampler

import exception.JNIException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DefaultSampler(
    private val sampler: NativeSampler,
) : Sampler {
    private val mutex = Mutex()

    private var currentVolume = 1f

    override var playbackSpeedFactor = MutableStateFlow(1f)

    override suspend fun setPlaybackSpeed(factor: Float) = mutex.withLock {
        runCatching {
            require(factor > 0f) { "Speed factor should be positive" }

            sampler.setPlaybackSpeed(factor)

            playbackSpeedFactor.emit(factor)
        }.recoverCatching(JNIException::create)
    }

    override suspend fun setVolume(value: Float) = mutex.withLock {
        runCatching {
            require(value in 0.0f..1.0f) { "Volume should be a value between 0.0f and 1.0f" }

            sampler.setVolume(value)

            currentVolume = value
        }.recoverCatching(JNIException::create)
    }

    override suspend fun setMuted(state: Boolean) = mutex.withLock {
        runCatching {
            sampler.setVolume(if (state) 0f else currentVolume)
        }.recoverCatching(JNIException::create)
    }

    override suspend fun start() = mutex.withLock {
        runCatching {
            sampler.start()
        }.recoverCatching(JNIException::create)
    }

    override suspend fun play(bytes: ByteArray) = mutex.withLock {
        runCatching {
            sampler.play(bytes, bytes.size)
        }.recoverCatching(JNIException::create)
    }

    override suspend fun stop() = mutex.withLock {
        runCatching {
            sampler.stop()
        }.recoverCatching(JNIException::create)
    }

    override fun close() = runCatching { sampler.close() }.recoverCatching(JNIException::create).getOrThrow()
}