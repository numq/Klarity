package com.github.numq.klarity.core.sampler

import kotlinx.coroutines.flow.StateFlow

interface Sampler : AutoCloseable {
    val playbackSpeedFactor: StateFlow<Float>
    suspend fun getLatency(): Result<Long>
    suspend fun setPlaybackSpeed(factor: Float): Result<Unit>
    suspend fun setVolume(value: Float): Result<Unit>
    suspend fun setMuted(state: Boolean): Result<Unit>
    suspend fun start(): Result<Unit>
    suspend fun play(bytes: ByteArray): Result<Unit>
    suspend fun pause(): Result<Unit>
    suspend fun stop(): Result<Unit>

    companion object {
        internal fun create(sampleRate: Int, channels: Int): Result<Sampler> = runCatching {
            DefaultSampler(sampler = NativeSampler(sampleRate, channels))
        }
    }
}
