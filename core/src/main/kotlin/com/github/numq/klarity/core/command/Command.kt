package com.github.numq.klarity.core.command

import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.hwaccel.HardwareAccelerationFallback

sealed interface Command {
    enum class Descriptor {
        PREPARE, PLAY, PAUSE, RESUME, STOP, SEEK_TO, RELEASE
    }

    val descriptor: Descriptor

    data class Prepare(
        val location: String,
        val audioBufferSize: Int,
        val videoBufferSize: Int,
        val hardwareAcceleration: HardwareAcceleration,
        val hardwareAccelerationFallback: HardwareAccelerationFallback,
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