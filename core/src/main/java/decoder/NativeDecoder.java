package decoder;

public class NativeDecoder {

    private final long id;

    public NativeDecoder() {
        this.id = System.identityHashCode(this);
    }

    public long getId() {
        return id;
    }

    private native boolean initNative(long id, String location, boolean findAudioStream, boolean findVideoStream);

    private native NativeFormat getFormatNative(long id);

    private native NativeFrame nextFrameNative(long id);

    private native void seekToNative(long id, long timestampMicros);

    private native void resetNative(long id);

    private native void closeNative(long id);

    public boolean init(String location, boolean findAudioStream, boolean findVideoStream) {
        return initNative(id, location, findAudioStream, findVideoStream);
    }

    public NativeFormat getFormat() {
        return getFormatNative(id);
    }

    public NativeFrame nextFrame() {
        return nextFrameNative(id);
    }

    public void seekTo(long timestampMicros) {
        seekToNative(id, timestampMicros);
    }

    public void reset() {
        resetNative(id);
    }

    public void close() {
        closeNative(id);
    }
}