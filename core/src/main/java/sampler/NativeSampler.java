package sampler;

import java.lang.ref.Cleaner;

public class NativeSampler implements AutoCloseable {
    private final long nativeHandle;
    private final Cleaner.Cleanable cleanable;

    private static final Cleaner cleaner = Cleaner.create();

    private record CleanupAction(long handle) implements Runnable {
        @Override
        public void run() {
            deleteNative(handle);
        }
    }

    public NativeSampler(int sampleRate, int channels) throws Exception {
        long nativeHandle = createNative(sampleRate, channels);
        if (nativeHandle == 0) {
            throw new Exception("Unable to instantiate NativeSampler");
        }
        this.nativeHandle = nativeHandle;
        this.cleanable = cleaner.register(this, new CleanupAction(nativeHandle));
    }

    private static native long createNative(int sampleRate, int channels);

    private static native void setPlaybackSpeedNative(long handle, float factor);

    private static native void setVolumeNative(long handle, float value);

    private static native void startNative(long handle);

    private static native void playNative(long handle, byte[] bytes, int size);

    private static native void stopNative(long handle);

    private static native void deleteNative(long handle);

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
        cleanable.clean();
    }
}