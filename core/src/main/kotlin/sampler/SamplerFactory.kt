package sampler

import factory.Factory

object SamplerFactory : Factory<SamplerFactory.Parameters, Sampler> {
    data class Parameters(val sampleRate: Int, val channels: Int)

    override fun create(parameters: Parameters) = with(parameters) {
        Sampler.create(sampleRate = sampleRate, channels = channels)
    }
}