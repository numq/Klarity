#ifndef KLARITY_SAMPLER_ERROR_H
#define KLARITY_SAMPLER_ERROR_H

#include <stdexcept>

class SamplerException : public std::runtime_error {
public:
    explicit SamplerException(const std::string &message) : std::runtime_error(message) {}
};

#endif //KLARITY_SAMPLER_ERROR_H
