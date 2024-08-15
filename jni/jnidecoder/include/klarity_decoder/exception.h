#ifndef KLARITY_DECODER_EXCEPTION_H
#define KLARITY_DECODER_EXCEPTION_H

#include <stdexcept>

class MediaException : public std::runtime_error {
public:
    explicit MediaException(const std::string &message) : std::runtime_error(message) {}
};

class DecoderException : public std::runtime_error {
public:
    explicit DecoderException(const std::string &message) : std::runtime_error(message) {}
};

class MediaNotFoundException : public DecoderException {
public:
    MediaNotFoundException() : DecoderException("Media not found") {}
};

#endif //KLARITY_DECODER_EXCEPTION_H
