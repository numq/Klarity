package media

import frame.DecodedFrame
import decoder.Decoder
import javax.sound.sampled.AudioFormat

data class Media internal constructor(
    val url: String,
    val name: String?,
    val durationNanos: Long,
    val frameRate: Double = 0.0,
    val audioFormat: AudioFormat? = null,
    val size: Pair<Int, Int>? = null,
    val previewFrame: DecodedFrame.Video? = null,
) {
    companion object {
        fun create(url: String) = Decoder.createMedia(url)
    }
}