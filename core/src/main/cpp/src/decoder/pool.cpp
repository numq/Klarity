#include "pool.h"

FramePool::FramePool(const int capacity) : capacity(capacity) {
    std::lock_guard<std::mutex> lock(mutex);

    frames.reserve(capacity);

    for (int i = 0; i < capacity; ++i) {
        auto frame = std::unique_ptr<AVFrame, AVFrameDeleter>(av_frame_alloc());

        if (!frame) {
            throw DecoderException("Could not allocate audio frame for decoder pool");
        }

        frames.emplace_back(std::move(frame));
    }
}

std::shared_ptr<AVFrame> FramePool::acquire() {
    std::lock_guard<std::mutex> lock(mutex);

    return frames[index++ % capacity];
}