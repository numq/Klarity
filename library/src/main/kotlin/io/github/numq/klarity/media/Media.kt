package io.github.numq.klarity.media

import io.github.numq.klarity.format.AudioFormat
import io.github.numq.klarity.format.VideoFormat
import kotlin.time.Duration

sealed interface Media {
    val id: Long

    val location: String

    val duration: Duration

    val audioFormat: AudioFormat?

    val videoFormat: VideoFormat?

    data class Audio(
        override val id: Long,
        override val location: String,
        override val duration: Duration,
        val format: AudioFormat,
    ) : Media {
        override val audioFormat = format

        override val videoFormat = null
    }

    data class Video(
        override val id: Long,
        override val location: String,
        override val duration: Duration,
        val format: VideoFormat,
    ) : Media {
        override val audioFormat = null

        override val videoFormat = format
    }

    data class AudioVideo(
        override val id: Long,
        override val location: String,
        override val duration: Duration,
        override val audioFormat: AudioFormat,
        override val videoFormat: VideoFormat,
    ) : Media

    fun isContinuous() = duration.isPositive() && (audioFormat != null || (videoFormat?.frameRate ?: 0.0) > 0.0)
}