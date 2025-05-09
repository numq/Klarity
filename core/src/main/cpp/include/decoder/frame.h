#ifndef KLARITY_DECODER_FRAME_H
#define KLARITY_DECODER_FRAME_H

#include <vector>

struct AudioFrame {
    std::vector<uint8_t> bytes;
    int64_t timestampMicros;
};

struct VideoFrame {
    int remaining;
    int64_t timestampMicros;
};

#endif //KLARITY_DECODER_FRAME_H