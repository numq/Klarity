package media

import javax.sound.sampled.AudioFormat

data class Media(
    val name: String,
    val durationNanos: Long,
    val audioFormat: AudioFormat? = null,
    val frameRate: Double = 0.0,
    val width: Int = 0,
    val height: Int = 0,
)