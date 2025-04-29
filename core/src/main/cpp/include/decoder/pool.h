#ifndef KLARITY_DECODER_POOL_H
#define KLARITY_DECODER_POOL_H

#include <memory>
#include <mutex>
#include <shared_mutex>
#include <vector>
#include "deleter.h"
#include "exception.h"
#include "frame.h"

extern "C" {
#include "libavcodec/avcodec.h"
}

class FramePool {
private:
    std::mutex mutex;

    int capacity;

    int index = 0;

    std::vector<std::shared_ptr<AVFrame>> frames;

public:
    explicit FramePool(int capacity);

    std::shared_ptr<AVFrame> acquire();
};

#endif //KLARITY_DECODER_POOL_H