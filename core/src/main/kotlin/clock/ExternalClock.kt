package clock

import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.nanoseconds

internal class ExternalClock : Clock {
    private var playbackSpeedFactor = 1.0
    private var startNanos = Duration.INFINITE

    override fun getElapsedMicros() = runCatching {
        check(startNanos.isFinite()) { "Clock has not started yet" }

        ((System.nanoTime().nanoseconds - startNanos) * playbackSpeedFactor).inWholeMicroseconds
    }

    override fun setPlaybackSpeed(factor: Double) = runCatching {
        require(factor > 0f) { "Speed factor should be positive" }
    }.mapCatching {
        playbackSpeedFactor = factor
        startNanos = System.nanoTime().nanoseconds - getElapsedMicros().getOrThrow().microseconds / factor
    }

    override fun start(timeShiftMicros: Long) = runCatching {
        startNanos = System.nanoTime().nanoseconds - timeShiftMicros.microseconds
    }
}