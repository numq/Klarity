#ifndef KLARITY_DECODER_DECODER_H
#define KLARITY_DECODER_DECODER_H

#include <memory>
#include <mutex>
#include <optional>
#include <shared_mutex>
#include <string>
#include "deleter.h"
#include "exception.h"
#include "format.h"
#include "frame.h"
#include "hwaccel.h"

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/imgutils.h>
}

class Decoder {
private:
    std::shared_mutex mutex;

    const int THREAD_COUNT = 2;

    const AVSampleFormat targetSampleFormat = AV_SAMPLE_FMT_FLT;

    const AVPixelFormat targetPixelFormat = AV_PIX_FMT_BGRA;

    const int swsFlags = SWS_BILINEAR;

    int swsWidth = -1;

    int swsHeight = -1;

    AVPixelFormat swsPixelFormat = AV_PIX_FMT_NONE;

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

    std::vector<uint8_t> audioBuffer;

public:
    static AVPixelFormat _getHardwareAccelerationFormat(
            AVCodecContext *codecContext,
            const AVPixelFormat *pixelFormats
    );

    bool _isValid();

    bool _hasAudio();

    bool _hasVideo();

    bool _isHardwareAccelerated();

    bool _prepareHardwareAcceleration(uint32_t deviceType);

    int _processAudio();

    int _processVideo(uint8_t *buffer);

public:
    Decoder(
            const std::string &location,
            bool findAudioStream,
            bool findVideoStream,
            bool decodeAudioStream,
            bool decodeVideoStream,
            const std::vector<uint32_t> &hardwareAccelerationCandidates
    );

    ~Decoder();

    Decoder(const Decoder &) = delete;

    Decoder &operator=(const Decoder &) = delete;

    Format format;

    std::optional<AudioFrame> decodeAudio();

    std::optional<VideoFrame> decodeVideo(uint8_t *buffer, int capacity);

    void seekTo(long timestampMicros, bool keyFramesOnly);

    void reset();
};

#endif // KLARITY_DECODER_DECODER_H