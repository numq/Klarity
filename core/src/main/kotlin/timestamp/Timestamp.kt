package timestamp

import kotlin.time.Duration.Companion.microseconds

data class Timestamp(val micros: Long, val millis: Long = micros.microseconds.inWholeMilliseconds) {
    companion object {
        val ZERO = Timestamp(0L)
    }
}