package sampler

interface Sampler : AutoCloseable {
    val playbackSpeedFactor: Float
    suspend fun setPlaybackSpeed(factor: Float): Result<Unit>
    suspend fun setVolume(value: Float): Result<Unit>
    suspend fun setMuted(state: Boolean): Result<Unit>
    suspend fun start(): Result<Unit>
    suspend fun play(bytes: ByteArray): Result<Unit>
    suspend fun pause(): Result<Unit>
    suspend fun resume(): Result<Unit>
    suspend fun stop(): Result<Unit>

    companion object {
        internal fun create(sampleRate: Int, channels: Int): Result<Sampler> = runCatching {
            val nativeSampler = NativeSampler()

            nativeSampler.initialize(sampleRate, channels)

            DefaultSampler(nativeSampler = nativeSampler)
        }
    }
}
