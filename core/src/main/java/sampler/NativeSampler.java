package sampler;

public class NativeSampler implements AutoCloseable {
    private final long nativeHandle;

    public NativeSampler(int sampleRate, int channels) {
        this.nativeHandle = createNative(sampleRate, channels);
    }

    private native long createNative(int sampleRate, int channels);

    private native void setPlaybackSpeedNative(long handle, float factor);

    private native void setVolumeNative(long handle, float value);

    private native void startNative(long handle);

    private native void playNative(long handle, byte[] bytes, int size);

    private native void stopNative(long handle);

    private native void deleteNative(long handle);

    public void setPlaybackSpeed(float factor) {
        setPlaybackSpeedNative(nativeHandle, factor);
    }

    public void setVolume(float value) {
        setVolumeNative(nativeHandle, value);
    }

    public void start() {
        startNative(nativeHandle);
    }

    public void play(byte[] data, int size) {
        playNative(nativeHandle, data, size);
    }

    public void stop() {
        stopNative(nativeHandle);
    }

    @Override
    public void close() {
        deleteNative(nativeHandle);
    }
}