package media

import format.AudioFormat
import format.VideoFormat

data class Media(
    val id: Long,
    val location: Location,
    val durationMicros: Long,
    val audioFormat: AudioFormat?,
    val videoFormat: VideoFormat?,
)