package decoder;

import java.util.UUID;

public class NativeDecoder {

    private final long id;

    public NativeDecoder() {
        this.id = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    }

    public long getId() {
        return id;
    }

    private native void initNative(long id, String location, boolean findAudioStream, boolean findVideoStream);

    private native NativeFormat getFormatNative(long id);

    private native NativeFrame nextFrameNative(long id);

    private native void seekToNative(long id, long timestampMicros);

    private native void resetNative(long id);

    private native void closeNative(long id);

    public void init(String location, boolean findAudioStream, boolean findVideoStream) {
        initNative(id, location, findAudioStream, findVideoStream);
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