package format

data class AudioFormat(
    val sampleRate: Int,
    val channels: Int,
) {
    companion object {
        const val MIN_BUFFER_SIZE = 4
    }
}