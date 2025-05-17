package io.github.numq.klarity.sampler

import io.github.numq.klarity.frame.Frame

interface Sampler {
    suspend fun getLatency(): Result<Long>

    suspend fun start(): Result<Unit>

    suspend fun write(frame: Frame.Content.Audio, volume: Float, playbackSpeedFactor: Float): Result<Unit>

    suspend fun stop(): Result<Unit>

    suspend fun flush(): Result<Unit>

    suspend fun drain(volume: Float, playbackSpeedFactor: Float): Result<Unit>

    suspend fun close(): Result<Unit>

    companion object {
        internal fun create(sampleRate: Int, channels: Int): Result<Sampler> = runCatching {
            DefaultSampler(sampler = NativeSampler(sampleRate, channels))
        }
    }
}
