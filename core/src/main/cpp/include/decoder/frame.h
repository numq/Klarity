#ifndef KLARITY_DECODER_FRAME_H
#define KLARITY_DECODER_FRAME_H

#include <cstdint>
#include <vector>

struct AudioFrame {
    int64_t timestampMicros;
    std::vector<uint8_t> audioBytes;
};

struct VideoFrame {
    int64_t timestampMicros;
};

#endif //KLARITY_DECODER_FRAME_H