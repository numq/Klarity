#ifndef KLARITY_DECODER_FRAME_H
#define KLARITY_DECODER_FRAME_H

#include <cstdint>
#include <vector>

struct Frame {
    enum Type {
        AUDIO, VIDEO
    };

    Type type;
    uint32_t width;
    uint32_t height;
    int64_t timestampMicros;
    std::vector<uint8_t> bytes;
};

#endif //KLARITY_DECODER_FRAME_H