package sampler;

public class NativeSampler {
    private final long nativeHandle;

    public NativeSampler(int sampleRate, int channels) {
        this.nativeHandle = initializeNative(sampleRate, channels);
    }

    private native void setPlaybackSpeedNative(long nativeHandle, float factor);

    private native void setVolumeNative(long nativeHandle, float value);

    private native long initializeNative(int sampleRate, int channels);

    private native void startNative(long nativeHandle);

    private native void playNative(long nativeHandle, byte[] bytes, int size);

    private native void stopNative(long nativeHandle);

    private native void closeNative(long nativeHandle);

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

    public void close() {
        closeNative(nativeHandle);
    }
}