package settings

import format.AudioFormat
import format.VideoFormat

data class Settings(
    val playbackSpeedFactor: Float,
    val isMuted: Boolean,
    val volume: Float,
    val audioBufferSize: Int,
    val videoBufferSize: Int,
) {
    companion object {
        val DEFAULT_SETTINGS = Settings(
            playbackSpeedFactor = 1.0f,
            isMuted = false,
            volume = 1.0f,
            audioBufferSize = AudioFormat.MIN_BUFFER_SIZE,
            videoBufferSize = VideoFormat.MIN_BUFFER_SIZE,
        )
    }
}