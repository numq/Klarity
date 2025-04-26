#ifndef KLARITY_DECODER_FRAME_H
#define KLARITY_DECODER_FRAME_H

#include <vector>

enum FrameType {
    AUDIO, VIDEO
};

struct Frame {
    void *buffer;
    size_t size;
    int64_t timestampMicros;
    FrameType type;
};

#endif //KLARITY_DECODER_FRAME_H