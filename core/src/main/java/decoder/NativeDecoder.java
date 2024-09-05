package decoder;

import java.lang.ref.Cleaner;

public class NativeDecoder implements AutoCloseable {
    private final long nativeHandle;
    private final Cleaner.Cleanable cleanable;

    private static final Cleaner cleaner = Cleaner.create();

    private record CleanupAction(long handle) implements Runnable {
        @Override
        public void run() {
            deleteNative(handle);
        }
    }

    public NativeDecoder(String location, boolean findAudioStream, boolean findVideoStream) throws Exception {
        long nativeHandle = createNative(location, findAudioStream, findVideoStream);
        if (nativeHandle == 0) {
            throw new Exception("Unable to instantiate NativeDecoder");
        }
        this.nativeHandle = nativeHandle;
        this.cleanable = cleaner.register(this, new CleanupAction(nativeHandle));
    }

    private static native long createNative(String location, boolean findAudioStream, boolean findVideoStream);

    private static native NativeFormat getFormatNative(long handle);

    private static native NativeFrame nextFrameNative(long handle, int width, int height);

    private static native void seekToNative(long handle, long timestampMicros, boolean keyframesOnly);

    private static native void resetNative(long handle);

    private static native void deleteNative(long handle);

    public NativeFormat getFormat() {
        return getFormatNative(nativeHandle);
    }

    public NativeFrame nextFrame(Integer width, Integer height) {
        return nextFrameNative(nativeHandle, width == null ? getFormat().width() : width, height == null ? getFormat().height() : height);
    }

    public void seekTo(long timestampMicros, boolean keyframesOnly) {
        seekToNative(nativeHandle, timestampMicros, keyframesOnly);
    }

    public void reset() {
        resetNative(nativeHandle);
    }

    @Override
    public void close() {
        cleanable.clean();
    }
}