#ifndef KLARITY_DECODER_FRAME_H
#define KLARITY_DECODER_FRAME_H

#include <cstdint>
#include <vector>

struct Frame {
    enum Type {
        AUDIO, VIDEO
    };

    Type type;
    int64_t timestampMicros;
    uint32_t writtenBytes;
};

#endif //KLARITY_DECODER_FRAME_H