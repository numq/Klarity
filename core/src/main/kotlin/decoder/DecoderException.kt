package decoder

sealed class DecoderException private constructor(message: String) : Exception(message) {
    object AlreadyInitialized : DecoderException("Decoder is already initialized")
    object UnableToInitialize : DecoderException("Unable to initialize decoder")
}