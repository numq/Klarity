package io.github.numq.example.slider

internal fun sliderTransform(value: Float, valueRange: ClosedRange<Float>, trackRange: ClosedRange<Float>): Float {
    val clampedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val valueRangeLength = valueRange.endInclusive - valueRange.start
    val trackRangeLength = trackRange.endInclusive - trackRange.start
    return trackRange.start + (clampedValue - valueRange.start) / valueRangeLength * trackRangeLength
}