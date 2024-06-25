package clock

interface Clock {
    fun getElapsedMicros(): Long
    fun setPlaybackSpeed(factor: Double)
    fun start(timeShiftMicros: Long)

    companion object {
        internal fun create(): Result<Clock> = runCatching { ExternalClock() }
    }
}