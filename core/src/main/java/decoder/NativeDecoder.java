package decoder;

public class NativeDecoder {

    private final long id;

    private NativeFormat format;

    public NativeDecoder() {
        this.id = System.identityHashCode(this);
    }

    public long getId() {
        return id;
    }

    public NativeFormat getFormat() {
        return format;
    }

    private native NativeFormat initNative(long id, String location, boolean findAudioStream, boolean findVideoStream);

    private native NativeFrame readFrameNative(long id, boolean doVideo, boolean doAudio);

    private native void seekToNative(long id, long timestampMicros);

    private native void resetNative(long id);

    private native void closeNative(long id);

    public boolean init(String location, boolean findAudioStream, boolean findVideoStream) {
        this.format = initNative(id, location, findAudioStream, findVideoStream);
        return this.format != null;
    }

    public NativeFrame readFrame(boolean doVideo, boolean doAudio) {
        return readFrameNative(id, doVideo, doAudio);
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