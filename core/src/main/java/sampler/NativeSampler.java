package sampler;

import java.util.UUID;

public class NativeSampler {
    public final long id;

    public NativeSampler() {
        this.id = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }

    public long getId() {
        return id;
    }

    private native void setPlaybackSpeedNative(long id, float factor);

    private native void setVolumeNative(long id, float value);

    private native void initializeNative(long id, int sampleRate, int channels);

    private native void startNative(long id);

    private native void playNative(long id, byte[] bytes, int size);

    private native void stopNative(long id);

    private native void closeNative(long id);

    public void setPlaybackSpeed(float factor) {
        setPlaybackSpeedNative(id, factor);
    }

    public void setVolume(float value) {
        setVolumeNative(id, value);
    }

    public void initialize(int sampleRate, int channels) {
        initializeNative(id, sampleRate, channels);
    }

    public void start() {
        startNative(id);
    }

    public void play(byte[] data, int size) {
        playNative(id, data, size);
    }

    public void stop() {
        stopNative(id);
    }

    public void close() {
        closeNative(id);
    }
}