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

        check(nativeSampler.setPlaybackSpeed(factor)) { "Unable to set sampler playback speed" }

        playbackSpeedFactor = factor
        playbackSpeedFactor
    }

    override suspend fun setVolume(value: Float) = runCatching {
        require(value > 0f) { "Volume value should be positive" }

        check(nativeSampler.setVolume(value)) { "Unable to set sampler volume" }

        currentVolume = value
        currentVolume
    }

    override suspend fun setMuted(state: Boolean) = runCatching {
        check(nativeSampler.setVolume(if (state) 0f else currentVolume)) { "Unable to mute sampler" }

        state
    }

    override suspend fun getCurrentTime() = runCatching { nativeSampler.currentTime }

    override suspend fun play(bytes: ByteArray) = runCatching {
        nativeSampler.play(bytes, bytes.size)
    }.recoverCatching {
        throw Exception("Unable to play sampler: ${it.localizedMessage}")
    }

    override suspend fun pause() = runCatching {
        nativeSampler.pause()
    }.recoverCatching {
        throw Exception("Unable to pause sampler: ${it.localizedMessage}")
    }

    override suspend fun resume() = runCatching {
        nativeSampler.resume()
    }.recoverCatching {
        throw Exception("Unable to resume sampler: ${it.localizedMessage}")
    }

    override suspend fun stop() = runCatching {
        nativeSampler.stop()
    }.recoverCatching {
        throw Exception("Unable to stop sampler: ${it.localizedMessage}")
    }

    override fun close() = runCatching { nativeSampler.close() }.getOrDefault(Unit)
}