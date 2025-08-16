package io.github.numq.klarity.command

import io.github.numq.klarity.hwaccel.HardwareAcceleration
import kotlin.time.Duration

internal sealed interface Command {
    enum class Descriptor {
        PREPARE, PLAY, PAUSE, RESUME, STOP, SEEK_TO, RELEASE
    }

    val descriptor: Descriptor

    data class Prepare(
        val location: String,
        val audioBufferSize: Int,
        val videoBufferSize: Int,
        val hardwareAccelerationCandidates: List<HardwareAcceleration>?,
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

    data class SeekTo(val timestamp: Duration) : Command {
        override val descriptor = Descriptor.SEEK_TO
    }

    data object Release : Command {
        override val descriptor = Descriptor.RELEASE
    }
}