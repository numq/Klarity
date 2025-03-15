#ifndef KLARITY_DECODER_DECODER_H
#define KLARITY_DECODER_DECODER_H

#include <string>
#include <iostream>
#include <memory>
#include <mutex>
#include <optional>
#include "frame.h"
#include "format.h"
#include "exception.h"

extern "C" {
#include "libavutil/imgutils.h"
#include "libswresample/swresample.h"
#include "libswscale/swscale.h"
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
}

struct Decoder {
private:
    class AVPacketGuard {
    public:
        AVPacketGuard() {
            packet = av_packet_alloc();
        }

        ~AVPacketGuard() {
            if (packet) {
                av_packet_free(&packet);
            }
        }

        AVPacket *get() { return packet; }

    private:
        AVPacket *packet = nullptr;
    };

    class AVFrameGuard {
    public:
        AVFrameGuard() {
            frame = av_frame_alloc();
        }

        ~AVFrameGuard() {
            if (frame) {
                av_frame_free(&frame);
            }
        }

        AVFrame *get() { return frame; }

    private:
        AVFrame *frame = nullptr;
    };

    const AVSampleFormat sampleFormat = AV_SAMPLE_FMT_FLT;

    const AVPixelFormat pixelFormat = AV_PIX_FMT_RGBA;

    std::mutex mutex;

    std::vector<uint8_t> audioBuffer;

    std::vector<uint8_t> videoBuffer;

    AVFormatContext *formatContext = nullptr;

    AVCodecContext *audioCodecContext = nullptr;

    AVCodecContext *videoCodecContext = nullptr;

    SwrContext *swrContext = nullptr;

    SwsContext *swsContext = nullptr;

    int audioStreamIndex = -1;

    int videoStreamIndex = -1;

    AVCodecContext *_initCodecContext(unsigned int streamIndex);

    std::vector<uint8_t> &_processAudioFrame(const AVFrame &src);

    std::vector<uint8_t> &_processVideoFrame(const AVFrame &src, int64_t width, int64_t height);

    void _cleanUp();

public:
    Format format;

    explicit Decoder(const std::string &location, bool findAudioStream, bool findVideoStream);

    ~Decoder();

    std::optional<Frame> nextFrame(int64_t width, int64_t height);

    void seekTo(long timestampMicros, bool keyframesOnly);

    void reset();
};

#endif // KLARITY_DECODER_DECODER_H