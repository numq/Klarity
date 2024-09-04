package sampler

import factory.SuspendFactory

class SamplerFactory : SuspendFactory<SamplerFactory.Parameters, Sampler> {
    data class Parameters(val sampleRate: Int, val channels: Int)

    override suspend fun create(parameters: Parameters) = with(parameters) {
        Sampler.create(sampleRate = sampleRate, channels = channels)
    }
}