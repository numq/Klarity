package io.github.numq.example.slider

fun sliderTransform(value: Float, valueRange: ClosedRange<Float>, trackRange: ClosedRange<Float>): Float {
    val clampedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)

    val normalized = (clampedValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)

    return trackRange.start + normalized * (trackRange.endInclusive - trackRange.start)
}