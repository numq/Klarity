package sampler

internal class DefaultSampler(
    private val nativeSampler: NativeSampler,
) : Sampler {
    @Volatile
    private var currentVolume: Float = 1f

    @Volatile
    override var playbackSpeedFactor: Float = 1.0f

    override suspend fun setPlaybackSpeed(factor: Float) = runCatching {
        require(factor > 0f) { "Speed factor should be positive" }

        nativeSampler.setPlaybackSpeed(factor)

        playbackSpeedFactor = factor
    }

    override suspend fun setVolume(value: Float) = runCatching {
        require(value in 0.0f..1.0f) { "Volume should be a value between 0.0f and 1.0f" }

        nativeSampler.setVolume(value)

        currentVolume = value
    }

    override suspend fun setMuted(state: Boolean) = runCatching {
        nativeSampler.setVolume(if (state) 0f else currentVolume)
    }

    override suspend fun start() = runCatching {
        nativeSampler.start()
    }

    override suspend fun play(bytes: ByteArray) = runCatching {
        nativeSampler.play(bytes, bytes.size)
    }

    override suspend fun stop() = runCatching {
        nativeSampler.stop()
    }

    override fun close() = runCatching { nativeSampler.close() }.getOrDefault(Unit)
}