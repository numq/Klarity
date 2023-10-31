package media

data class MediaSettings(
    val mediaUrl: String,
    val hasAudio: Boolean,
    val hasVideo: Boolean,
    val imageSize: ImageSize? = null,
)