#ifndef KLARITY_DECODER_POOL_H
#define KLARITY_DECODER_POOL_H

#include <memory>
#include <mutex>
#include <vector>
#include "exception.h"
#include "frame.h"

extern "C" {
#include "libavutil/imgutils.h"
}

template<typename T>
class Pool {
private:
    std::mutex mutex;

    size_t capacity;

    int index = 0;

    std::vector<std::unique_ptr<T>> items;

public:
    template<typename... Args>
    explicit Pool(size_t capacity, Args &&... args) : capacity(capacity) {
        items.reserve(capacity);

        for (size_t i = 0; i < capacity; ++i) {
            items.emplace_back(std::make_unique<T>(std::forward<Args>(args)...));
        }
    }

    Pool(const Pool &) = delete;

    Pool &operator=(const Pool &) = delete;

    T *acquire() {
        std::lock_guard<std::mutex> lock(mutex);

        if (items.empty()) {
            throw DecoderException("Pool is empty");
        }

        return items[index++ % capacity].get();
    }
};

class AudioBufferPoolItem {
public:
    std::vector<uint8_t> buffer;
};

class VideoBufferPoolItem {
public:
    std::vector<uint8_t> buffer;

    std::vector<uint8_t *> planes;

    std::vector<int> strides;

    int width;

    int height;

    AVPixelFormat format;

    VideoBufferPoolItem(int w, int h, AVPixelFormat fmt) : width(w), height(h), format(fmt) {
        auto size = av_image_get_buffer_size(fmt, w, h, 1);

        if (size < 0) {
            throw DecoderException("Could not get video buffer size");
        }

        size += AV_INPUT_BUFFER_PADDING_SIZE;

        buffer.resize(size);

        planes.resize(AV_NUM_DATA_POINTERS);

        strides.resize(AV_NUM_DATA_POINTERS);

        if (av_image_fill_arrays(
                planes.data(),
                strides.data(),
                buffer.data(),
                format,
                width,
                height,
                1
        ) <= 0) {
            throw DecoderException("Could not fill video buffers");
        }
    }
};

using AudioBufferPool = Pool<AudioBufferPoolItem>;

using VideoBufferPool = Pool<VideoBufferPoolItem>;

#endif //KLARITY_DECODER_POOL_H