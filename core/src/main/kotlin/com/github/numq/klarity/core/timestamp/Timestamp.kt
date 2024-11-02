package com.github.numq.klarity.core.timestamp

import kotlin.time.Duration.Companion.microseconds

/**
 * Data class representing a timestamp in microseconds and milliseconds.
 *
 * @property micros The timestamp in microseconds.
 * @property millis The timestamp in milliseconds, derived from micros.
 */
data class Timestamp(val micros: Long, val millis: Long = micros.microseconds.inWholeMilliseconds) {
    companion object {
        /**
         * Constant representing a zero timestamp.
         */
        val ZERO = Timestamp(0L)
    }
}
