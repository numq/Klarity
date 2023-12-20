package media

import frame.DecodedFrame
import javax.sound.sampled.AudioFormat

data class MediaInfo(
    val durationNanos: Long,
    val audioFormat: AudioFormat? = null,
    val frameRate: Double? = null,
    val size: Pair<Int, Int>? = null,
    val previewFrame: DecodedFrame.Video? = null,
)