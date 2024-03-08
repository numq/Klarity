#ifndef KLARITY_DECODER_FRAME_H
#define KLARITY_DECODER_FRAME_H

#include <cstdint>
#include <vector>

struct Frame {
    enum FrameType {
        AUDIO, VIDEO
    };

    FrameType type;
    int64_t timestampMicros;
    std::vector<uint8_t> bytes{};

public:
    Frame(FrameType type, int64_t timestampMicros, std::vector<uint8_t> bytes);

    ~Frame();
};

#endif //KLARITY_DECODER_FRAME_H
