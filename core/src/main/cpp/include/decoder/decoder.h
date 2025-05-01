#ifndef KLARITY_DECODER_DECODER_H
#define KLARITY_DECODER_DECODER_H

#include <memory>
#include <mutex>
#include <shared_mutex>
#include <string>
#include "deleter.h"
#include "exception.h"
#include "format.h"
#include "frame.h"
#include "hwaccel.h"
#include "pool.h"

extern "C" {
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libavutil/imgutils.h"
#include <libavutil/opt.h>
#include "libswresample/swresample.h"
#include "libswscale/swscale.h"
}

class Decoder {
private:
    std::shared_mutex mutex;

    const AVSampleFormat targetSampleFormat = AV_SAMPLE_FMT_FLT;

    const AVPixelFormat targetPixelFormat = AV_PIX_FMT_BGRA;

    int targetSampleRate;

    AVChannelLayout targetChannelLayout = AVChannelLayout{};

    int targetWidth;

    int targetHeight;

    const int swsFlags = SWS_BILINEAR;

    AVPixelFormat swsPixelFormat = AV_PIX_FMT_NONE;

    int swsWidth = -1;

    int swsHeight = -1;

    std::unique_ptr<AVFormatContext, AVFormatContextDeleter> formatContext;

    std::unique_ptr<AVCodecContext, AVCodecContextDeleter> audioCodecContext;

    std::unique_ptr<AVCodecContext, AVCodecContextDeleter> videoCodecContext;

    const AVStream *audioStream = nullptr;

    const AVStream *videoStream = nullptr;

    const AVCodec *audioDecoder = nullptr;

    const AVCodec *videoDecoder = nullptr;

    std::unique_ptr<SwrContext, SwrContextDeleter> swrContext;

    std::unique_ptr<SwsContext, SwsContextDeleter> swsContext;

    std::unique_ptr<AVPacket, AVPacketDeleter> packet;

    std::unique_ptr<AVFrame, AVFrameDeleter> audioFrame;

    std::unique_ptr<AVFrame, AVFrameDeleter> swVideoFrame;

    std::unique_ptr<AVFrame, AVFrameDeleter> hwVideoFrame;

    std::unique_ptr<AudioBufferPool> audioBufferPool;

    std::unique_ptr<VideoBufferPool> videoBufferPool;

public:
    static AVPixelFormat _getHardwareAccelerationFormat(
            AVCodecContext *codecContext,
            const AVPixelFormat *pixelFormats
    );

    bool _hasAudio();

    bool _hasVideo();

    bool _isValid();

    bool _isHardwareAccelerated();

    bool _prepareHardwareAcceleration(uint32_t deviceType);

    void _processAudio(std::vector<uint8_t> &dst);

    void _processVideo(std::vector<uint8_t> &dst, std::vector<uint8_t *> &planes, std::vector<int> &strides);

public:
    Decoder(
            const std::string &location,
            int audioFramePoolCapacity,
            int videoFramePoolCapacity,
            int sampleRate,
            int channels,
            int width,
            int height,
            const std::vector<uint32_t> &hardwareAccelerationCandidates
    );

    ~Decoder();

    Decoder(const Decoder &) = delete;

    Decoder &operator=(const Decoder &) = delete;

    Format format;

    std::unique_ptr<Frame> decodeAudio();

    std::unique_ptr<Frame> decodeVideo();

    uint64_t seekTo(long timestampMicros, bool keyframesOnly);

    void reset();

    void releaseAudioBuffer(void *buffer);

    void releaseVideoBuffer(void *buffer);
};

#endif // KLARITY_DECODER_DECODER_H