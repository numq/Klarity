package io.github.numq.klarity.media

import io.github.numq.klarity.format.Format
import kotlin.time.Duration

data class Media(
    val id: Long,
    val location: String,
    val duration: Duration,
    val audioFormat: Format.Audio?,
    val videoFormat: Format.Video?,
) {
    fun isContinuous() = duration.isPositive() && (audioFormat != null || (videoFormat?.frameRate ?: 0.0) > 0.0)
}