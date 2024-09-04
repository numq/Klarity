package decoder;

public class NativeDecoder implements AutoCloseable {
    private final long nativeHandle;

    public NativeDecoder(String location, boolean findAudioStream, boolean findVideoStream) {
        this.nativeHandle = createNative(location, findAudioStream, findVideoStream);
    }

    private native long createNative(String location, boolean findAudioStream, boolean findVideoStream);

    private native NativeFormat getFormatNative(long handle);

    private native NativeFrame nextFrameNative(long handle, int width, int height);

    private native void seekToNative(long handle, long timestampMicros, boolean keyframesOnly);

    private native void resetNative(long handle);

    private native void deleteNative(long handle);

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
        deleteNative(nativeHandle);
    }
}