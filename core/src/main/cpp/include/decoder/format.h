#ifndef KLARITY_DECODER_FORMAT_H
#define KLARITY_DECODER_FORMAT_H

struct Format {
    std::string location;
    uint64_t durationMicros;
    uint32_t sampleRate = 0;
    uint32_t channels = 0;
    uint32_t width = 0;
    uint32_t height = 0;
    double frameRate = 0.0;
};

#endif //KLARITY_DECODER_FORMAT_H
