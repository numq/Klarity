package decoder;

public class NativeFormat {
    private final long durationMicros;
    private final int sampleRate;
    private final int channels;
    private final int width;
    private final int height;
    private final double frameRate;

    public NativeFormat(long durationMicros, int sampleRate, int channels, int width, int height, double frameRate) {
        this.durationMicros = durationMicros;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
    }

    public int getChannels() {
        return channels;
    }

    public long getDurationMicros() {
        return durationMicros;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public double getFrameRate() {
        return frameRate;
    }
}