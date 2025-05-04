#ifndef KLARITY_DECODER_FRAME_H
#define KLARITY_DECODER_FRAME_H

enum FrameType {
    AUDIO, VIDEO
};

struct Frame {
    int remaining;
    int64_t timestampMicros;
    FrameType type;
};

#endif //KLARITY_DECODER_FRAME_H