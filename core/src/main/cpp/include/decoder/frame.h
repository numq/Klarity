#ifndef KLARITY_DECODER_FRAME_H
#define KLARITY_DECODER_FRAME_H

#include <vector>

struct Frame {
    enum FrameType {
        AUDIO, VIDEO
    };

    FrameType type;
    int64_t timestampMicros;
    std::vector<uint8_t> bytes;
};

#endif //KLARITY_DECODER_FRAME_H
