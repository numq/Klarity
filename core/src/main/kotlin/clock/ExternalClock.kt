package clock

import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.nanoseconds

internal class ExternalClock : Clock {
    private var playbackSpeedFactor = 1.0
    private var startNanos = Duration.INFINITE

    override fun getElapsedMicros(): Long {
        check(startNanos.isFinite()) { "Clock has not started yet" }

        val elapsedTime = System.nanoTime().nanoseconds - startNanos
        return (elapsedTime * playbackSpeedFactor).inWholeMicroseconds
    }

    override fun setPlaybackSpeed(factor: Double) {
        if (factor > 0) {
            val elapsedTime = getElapsedMicros().microseconds
            playbackSpeedFactor = factor
            startNanos = System.nanoTime().nanoseconds - elapsedTime / factor
        }
    }

    override fun start(timeShiftMicros: Long) {
        startNanos = System.nanoTime().nanoseconds - timeShiftMicros.microseconds
    }
}