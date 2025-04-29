#ifndef KLARITY_DECODER_FRAME_H
#define KLARITY_DECODER_FRAME_H

enum FrameType {
    AUDIO, VIDEO
};

struct Frame {
    uint8_t *buffer;
    int size;
    int64_t timestampMicros;
    FrameType type;
};

#endif //KLARITY_DECODER_FRAME_H