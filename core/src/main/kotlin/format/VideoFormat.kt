package format

data class VideoFormat(
    val width: Int,
    val height: Int,
    val frameRate: Double,
) {
    companion object {
        const val MIN_BUFFER_SIZE = 2
    }
}