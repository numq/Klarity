package decoder;

public class NativeFrame {
    public enum Type {
        AUDIO, VIDEO
    }

    private final int type;
    private final long timestampMicros;
    private final byte[] bytes;

    public NativeFrame(int type, long timestampMicros, byte[] bytes) {
        this.type = type;
        this.timestampMicros = timestampMicros;
        this.bytes = bytes;
    }

    public int getType() {
        return type;
    }

    public long getTimestampMicros() {
        return timestampMicros;
    }

    public byte[] getBytes() {
        return bytes;
    }
}