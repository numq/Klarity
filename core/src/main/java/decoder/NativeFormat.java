package decoder;

public class NativeFormat {
    private final String location;
    private final long durationMicros;
    private final int sampleRate;
    private final int channels;
    private final int width;
    private final int height;
    private final double frameRate;

    public NativeFormat(String location, long durationMicros, int sampleRate, int channels, int width, int height, double frameRate) {
        this.location = location;
        this.durationMicros = durationMicros;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
    }

    public String getLocation() {
        return location;
    }

    public long getDurationMicros() {
        return durationMicros;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannels() {
        return channels;
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