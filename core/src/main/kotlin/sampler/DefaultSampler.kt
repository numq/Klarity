package sampler

import exception.JNIException
import kotlinx.coroutines.Dispatchers

internal class DefaultSampler(
    private val nativeSampler: NativeSampler,
) : Sampler {
    @Volatile
    private var currentVolume: Float = 1f

    @Volatile
    override var playbackSpeedFactor: Float = 1.0f

    override suspend fun setPlaybackSpeed(factor: Float) = with(Dispatchers.Default) {
        runCatching {
            require(factor > 0f) { "Speed factor should be positive" }

            nativeSampler.setPlaybackSpeed(factor)

            playbackSpeedFactor = factor
        }.recoverCatching(JNIException::create)
    }

    override suspend fun setVolume(value: Float) = with(Dispatchers.Default) {
        runCatching {
            require(value in 0.0f..1.0f) { "Volume should be a value between 0.0f and 1.0f" }

            nativeSampler.setVolume(value)

            currentVolume = value
        }.recoverCatching(JNIException::create)
    }

    override suspend fun setMuted(state: Boolean) = with(Dispatchers.Default) {
        runCatching {
            nativeSampler.setVolume(if (state) 0f else currentVolume)
        }.recoverCatching(JNIException::create)
    }

    override suspend fun start() = with(Dispatchers.Default) {
        runCatching {
            nativeSampler.start()
        }.recoverCatching(JNIException::create)
    }

    override suspend fun play(bytes: ByteArray) = with(Dispatchers.Default) {
        runCatching {
            nativeSampler.play(bytes, bytes.size)
        }.recoverCatching(JNIException::create)
    }

    override suspend fun stop() = with(Dispatchers.Default) {
        runCatching {
            nativeSampler.stop()
        }.recoverCatching(JNIException::create)
    }

    override fun close() = runCatching { nativeSampler.close() }.getOrDefault(Unit)
}