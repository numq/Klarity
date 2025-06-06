#ifndef KLARITY_DECODER_EXCEPTION_H
#define KLARITY_DECODER_EXCEPTION_H

#include <stdexcept>
#include <string>

class DecoderException : public std::runtime_error {
public:
    explicit DecoderException(const std::string &message) : std::runtime_error(message) {}
};

class HardwareAccelerationException : public std::runtime_error {
public:
    explicit HardwareAccelerationException(const std::string &message) : std::runtime_error(message) {}
};

#endif //KLARITY_DECODER_EXCEPTION_H
