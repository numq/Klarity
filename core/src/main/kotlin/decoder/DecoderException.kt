package decoder

sealed class DecoderException private constructor(override val message: String) : Exception(message) {
    object UnableToCreate : DecoderException("Unable to create decoder")
}