package com.github.numq.klarity.core.media

import com.github.numq.klarity.core.format.AudioFormat
import com.github.numq.klarity.core.format.VideoFormat

sealed interface Media {
    val id: Long

    val location: Location

    val durationMicros: Long

    data class AudioVideo(
        override val id: Long,
        override val location: Location,
        override val durationMicros: Long,
        val audioFormat: AudioFormat,
        val videoFormat: VideoFormat,
    ) : Media {
        fun toAudio() = Audio(
            id = id,
            location = location,
            durationMicros = durationMicros,
            format = audioFormat
        )

        fun toVideo() = Video(
            id = id,
            location = location,
            durationMicros = durationMicros,
            format = videoFormat
        )
    }

    data class Audio(
        override val id: Long,
        override val location: Location,
        override val durationMicros: Long,
        val format: AudioFormat,
    ) : Media

    data class Video(
        override val id: Long,
        override val location: Location,
        override val durationMicros: Long,
        val format: VideoFormat,
    ) : Media
}