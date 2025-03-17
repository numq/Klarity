#ifndef KLARITY_DECODER_DECODER_H
#define KLARITY_DECODER_DECODER_H

#include <mutex>
#include <optional>
#include "exception.h"
#include "format.h"
#include "frame.h"

extern "C" {
#include "libavutil/imgutils.h"
#include "libswresample/swresample.h"
#include "libswscale/swscale.h"
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
}

class Decoder {
private:
    const AVSampleFormat sampleFormat = AV_SAMPLE_FMT_FLT;

    const AVPixelFormat pixelFormat = AV_PIX_FMT_RGBA;

    std::mutex mutex;

    std::vector<uint8_t> audioBuffer;

    std::vector<uint8_t> videoBuffer;

    AVFormatContext *formatContext = nullptr;

    AVCodecContext *audioCodecContext = nullptr;

    AVCodecContext *videoCodecContext = nullptr;

    int audioStreamIndex = -1;

    int videoStreamIndex = -1;

    SwrContext *swrContext = nullptr;

    SwsContext *swsContext = nullptr;

    int swsPixelFormat = AV_PIX_FMT_NONE;

    int swsWidth = -1;

    int swsHeight = -1;

    static AVCodecContext *_initializeCodecContext(AVCodecParameters *avCodecParameters);

    void _processAudioFrame(const AVFrame &src);

    void _processVideoFrame(const AVFrame &src, int dstWidth, int dstHeight);

    void _cleanUp();

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

public:
    Format format;

    Decoder(const std::string &location, bool findAudioStream, bool findVideoStream);

    ~Decoder();

    std::optional<Frame> nextFrame(int width, int height);

    void seekTo(long timestampMicros, bool keyframesOnly);

    void reset();
};

#endif // KLARITY_DECODER_DECODER_H