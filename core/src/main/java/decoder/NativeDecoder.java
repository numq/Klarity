package decoder;

public class NativeDecoder {
    private final long nativeHandle;

    public NativeDecoder(String location, boolean findAudioStream, boolean findVideoStream) {
        this.nativeHandle = initializeNative(location, findAudioStream, findVideoStream);
    }

    private native long initializeNative(String location, boolean findAudioStream, boolean findVideoStream);

    private native NativeFormat getFormatNative(long nativeHandle);

    private native NativeFrame nextFrameNative(long nativeHandle, int width, int height);

    private native void seekToNative(long nativeHandle, long timestampMicros, boolean keyframesOnly);

    private native void resetNative(long nativeHandle);

    private native void closeNative(long nativeHandle);

    public NativeFormat getFormat() {
        return getFormatNative(nativeHandle);
    }

    public NativeFrame nextFrame(Integer width, Integer height) {
        return nextFrameNative(nativeHandle, width == null ? getFormat().getWidth() : width, height == null ? getFormat().getHeight() : height);
    }

    public void seekTo(long timestampMicros, boolean keyframesOnly) {
        seekToNative(nativeHandle, timestampMicros, keyframesOnly);
    }

    public void reset() {
        resetNative(nativeHandle);
    }

    public void close() {
        closeNative(nativeHandle);
    }
}