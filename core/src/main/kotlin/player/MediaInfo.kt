package player

import javax.sound.sampled.AudioFormat

data class MediaInfo(
    val name: String,
    val durationNanos: Long,
    val audioFormat: AudioFormat,
    val frameRate: Double = 0.0,
    val width: Int = 0,
    val height: Int = 0,
)