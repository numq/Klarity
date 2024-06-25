package clock

import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.nanoseconds

internal class ExternalClock : Clock {
    @Volatile
    private var startNanos = Duration.INFINITE

    @Volatile
    private var playbackSpeedFactor = 1.0

    override suspend fun getElapsedMicros() = runCatching {
        check(startNanos.isFinite()) { "Clock has not started yet" }

        val elapsedTime = (System.nanoTime().nanoseconds - startNanos)

        (elapsedTime * playbackSpeedFactor).inWholeMicroseconds
    }

    override suspend fun setPlaybackSpeed(factor: Double) = runCatching {
        require(factor > 0) { "Speed factor should be positive" }

        val currentElapsedMicros = getElapsedMicros().getOrThrow().microseconds

        playbackSpeedFactor = factor

        startNanos = System.nanoTime().nanoseconds - (currentElapsedMicros / factor)
    }

    override suspend fun start(timeShiftMicros: Long) = runCatching {
        startNanos = System.nanoTime().nanoseconds - timeShiftMicros.microseconds
    }
}