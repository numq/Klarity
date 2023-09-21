package audio

sealed class AudioSamplerException private constructor(override val message: String) : Exception(message) {
    object FailedToCreate : AudioSamplerException("Failed to create audio sampler")
    data class FailedToInteract(
        val methodName: String,
    ) : AudioSamplerException("Failed to interact with method $methodName")
}