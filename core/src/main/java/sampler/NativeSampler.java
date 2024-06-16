package sampler;

public class NativeSampler {

    private final long id;

    public NativeSampler() {
        this.id = System.identityHashCode(this);
    }

    private native float getCurrentTimeNative(long id);

    private native boolean setPlaybackSpeedNative(long id, float factor);

    private native boolean setVolumeNative(long id, float value);

    private native boolean initNative(long id, int sampleRate, int channels, int numBuffers);

    private native boolean playNative(long id, byte[] bytes, int size);

    private native void pauseNative(long id);

    private native void resumeNative(long id);

    private native void stopNative(long id);

    private native void closeNative(long id);

    public float getCurrentTime() {
        return getCurrentTimeNative(id);
    }

    public boolean setPlaybackSpeed(float factor) {
        return setPlaybackSpeedNative(id, factor);
    }

    public boolean setVolume(float value) {
        return setVolumeNative(id, value);
    }

    public boolean init(int sampleRate, int channels, int numBuffers) {
        return initNative(id, sampleRate, channels, numBuffers);
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