package decoder;

public record NativeFormat(
        String location,
        long durationMicros,
        int sampleRate,
        int channels,
        int width,
        int height,
        double frameRate
) {
}