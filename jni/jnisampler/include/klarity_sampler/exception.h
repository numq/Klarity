#ifndef KLARITY_SAMPLER_ERROR_H
#define KLARITY_SAMPLER_ERROR_H

#include <stdexcept>

class MediaException : public std::runtime_error {
public:
    explicit MediaException(const std::string &message) : std::runtime_error(message) {}
};

class SamplerException : public std::runtime_error {
public:
    explicit SamplerException(const std::string &message) : std::runtime_error(message) {}
};

class MediaNotFoundException : public SamplerException {
public:
    MediaNotFoundException() : SamplerException("Media not found") {}
};

#endif //KLARITY_SAMPLER_ERROR_H
