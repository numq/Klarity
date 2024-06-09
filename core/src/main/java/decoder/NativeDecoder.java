package decoder;

public class NativeDecoder {

    private final long id;

    private String location;

    private NativeFormat format;

    public NativeDecoder() {
        this.id = System.identityHashCode(this);
    }

    public long getId() {
        return id;
    }

    public String getLocation() {
        return location;
    }

    public NativeFormat getFormat() {
        return format;
    }

    private native NativeFormat initNative(long id, String location, boolean findAudioStream, boolean findVideoStream);

    private native NativeFrame nextFrameNative(long id);

    private native void seekToNative(long id, long timestampMicros);

    private native void resetNative(long id);

    private native void closeNative(long id);

    public boolean init(String location, boolean findAudioStream, boolean findVideoStream) {
        this.location = location;
        this.format = initNative(id, location, findAudioStream, findVideoStream);
        return true;
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