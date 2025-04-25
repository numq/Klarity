#ifndef KLARITY_DECODER_FRAME_H
#define KLARITY_DECODER_FRAME_H

#include <vector>

struct Frame {
    void *buffer;
    size_t size;
    int64_t timestampMicros;
};

#endif //KLARITY_DECODER_FRAME_H