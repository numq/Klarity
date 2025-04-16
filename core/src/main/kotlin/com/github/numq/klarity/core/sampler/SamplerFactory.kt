package com.github.numq.klarity.core.sampler

import com.github.numq.klarity.core.factory.Factory

class SamplerFactory : Factory<SamplerFactory.Parameters, Sampler> {
    data class Parameters(val sampleRate: Int, val channels: Int)

    override fun create(parameters: Parameters) = with(parameters) {
        Sampler.create(sampleRate = sampleRate, channels = channels)
    }
}