package sampler

interface Sampler : AutoCloseable {
    val playbackSpeedFactor: Float
    suspend fun setPlaybackSpeed(factor: Float): Result<Float>
    suspend fun setVolume(value: Float): Result<Float>
    suspend fun setMuted(state: Boolean): Result<Boolean>
    suspend fun getCurrentTime(): Result<Float>
    suspend fun play(bytes: ByteArray): Result<Boolean>
    suspend fun pause(): Result<Unit>
    suspend fun resume(): Result<Unit>
    suspend fun stop(): Result<Unit>

    companion object {
        private const val NUM_BUFFERS = 4

        internal fun create(sampleRate: Int, channels: Int): Result<Sampler> = runCatching {
            val nativeSampler = NativeSampler()

            check(nativeSampler.init(sampleRate, channels, NUM_BUFFERS)) { "Unable to create sampler" }

            DefaultSampler(nativeSampler = nativeSampler)
        }
    }
}
