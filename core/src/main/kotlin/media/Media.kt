package media

import decoder.FrameDecoder

sealed class Media private constructor(open val info: MediaInfo) {
    data class Local(
        val path: String,
        val name: String,
        override val info: MediaInfo,
    ) : Media(info)

    data class Remote(
        val url: String,
        override val info: MediaInfo,
    ) : Media(info)

    companion object {
        suspend fun create(location: String): Media? =
            FrameDecoder.createMedia(location)?.takeIf { it.hasAudio() || it.hasVideo() }
    }

    fun hasAudio() = info.audioFormat != null

    fun hasVideo() = with(info) {
        frameRate != null && size != null
    }
}