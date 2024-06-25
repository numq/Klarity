package clock

interface Clock {
    suspend fun getElapsedMicros(): Result<Long>
    suspend fun setPlaybackSpeed(factor: Double): Result<Unit>
    suspend fun start(timeShiftMicros: Long): Result<Unit>

    companion object {
        internal fun create(): Result<Clock> = runCatching { ExternalClock() }
    }
}