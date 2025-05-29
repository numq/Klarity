package io.github.numq.klarity.sampler

import io.github.numq.klarity.factory.Factory

internal class SamplerFactory : Factory<SamplerFactory.Parameters, Sampler> {
    data class Parameters(val sampleRate: Int, val channels: Int)

    override fun create(parameters: Parameters) = with(parameters) {
        Sampler.create(sampleRate = sampleRate, channels = channels)
    }
}