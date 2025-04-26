package com.github.numq.klarity.core.sampler

import com.github.numq.klarity.core.frame.Frame

interface Sampler {
    suspend fun getLatency(): Result<Long>

    suspend fun setPlaybackSpeed(factor: Float): Result<Unit>

    suspend fun setVolume(value: Float): Result<Unit>

    suspend fun setMuted(state: Boolean): Result<Unit>

    suspend fun start(): Result<Unit>

    suspend fun play(frame: Frame.Content.Audio): Result<Unit>

    suspend fun pause(): Result<Unit>

    suspend fun stop(): Result<Unit>

    suspend fun close(): Result<Unit>

    companion object {
        internal fun create(sampleRate: Int, channels: Int): Result<Sampler> = runCatching {
            DefaultSampler(sampler = NativeSampler(sampleRate, channels))
        }
    }
}
