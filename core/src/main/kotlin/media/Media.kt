package media

import decoder.DecodedFrame
import javax.sound.sampled.AudioFormat

data class Media(
    val url: String,
    val name: String?,
    val durationNanos: Long,
    val audioFrameRate: Double = 0.0,
    val videoFrameRate: Double = 0.0,
    val audioFormat: AudioFormat? = null,
    val size: Pair<Int, Int>? = null,
    val previewFrame: DecodedFrame.Video? = null,
)