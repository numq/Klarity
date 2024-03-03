package sampler;

public class NativeSampler {

    private final long id;

    public NativeSampler() {
        this.id = System.identityHashCode(this);
    }

    private native boolean initNative(long id, int bitsPerSample, int sampleRate, int channels);

    private native boolean initNative(long id, int bitsPerSample, int sampleRate, int channels, int numBuffers);

    private native boolean setPlaybackSpeedNative(long id, float factor);

    private native boolean setVolumeNative(long id, float value);

    private native boolean playNative(long id, byte[] bytes, int size);

    private native void pauseNative(long id);

    private native void resumeNative(long id);

    private native void stopNative(long id);

    private native void closeNative(long id);

    public boolean init(int bitsPerSample, int sampleRate, int channels) {
        return initNative(id, bitsPerSample, sampleRate, channels);
    }

    public boolean init(int bitsPerSample, int sampleRate, int channels, int numBuffers) {
        return initNative(id, bitsPerSample, sampleRate, channels, numBuffers);
    }

    public boolean setPlaybackSpeed(float factor) {
        return setPlaybackSpeedNative(id, factor);
    }

    public boolean setVolume(float value) {
        return setVolumeNative(id, value);
    }

    public boolean play(byte[] data, int size) {
        return playNative(id, data, size);
    }

    public void pause() {
        pauseNative(id);
    }

    public void resume() {
        resumeNative(id);
    }

    public void stop() {
        stopNative(id);
    }

    public void close() {
        closeNative(id);
    }
}