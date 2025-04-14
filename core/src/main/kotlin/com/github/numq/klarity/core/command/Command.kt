package com.github.numq.klarity.core.command

import com.github.numq.klarity.core.hwaccel.HardwareAcceleration

sealed interface Command {
    enum class Descriptor {
        PREPARE, PLAY, PAUSE, RESUME, STOP, SEEK_TO, RELEASE
    }

    val descriptor: Descriptor

    data class Prepare(
        val location: String,
        val audioBufferSize: Int,
        val videoBufferSize: Int,
        val sampleRate: Int?,
        val channels: Int?,
        val width: Int?,
        val height: Int?,
        val frameRate: Double?,
        val hardwareAccelerationCandidates: List<HardwareAcceleration>?,
        val loadPreview: Boolean,
    ) : Command {
        override val descriptor = Descriptor.PREPARE
    }

    data object Play : Command {
        override val descriptor = Descriptor.PLAY
    }

    data object Pause : Command {
        override val descriptor = Descriptor.PAUSE
    }

    data object Resume : Command {
        override val descriptor = Descriptor.RESUME
    }

    data object Stop : Command {
        override val descriptor = Descriptor.STOP
    }

    data class SeekTo(val millis: Long) : Command {
        override val descriptor = Descriptor.SEEK_TO
    }

    data object Release : Command {
        override val descriptor = Descriptor.RELEASE
    }
}