package clock

interface Clock {
    fun getElapsedMicros(): Result<Long>
    fun setPlaybackSpeed(factor: Double): Result<Unit>
    fun start(timeShiftMicros: Long): Result<Unit>

    companion object {
        internal fun create(): Result<Clock> = runCatching { ExternalClock() }
    }
}