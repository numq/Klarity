#include "sampler.h"

Sampler::Sampler(uint32_t sampleRate, uint32_t channels) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    this->sampleRate = sampleRate;

    this->channels = channels;

    stretch = std::make_unique<signalsmith::stretch::SignalsmithStretch<float>>();

    stretch->presetDefault(static_cast<int>(channels), static_cast<float>(sampleRate));

    PaDeviceIndex deviceIndex = Pa_GetDefaultOutputDevice();
    if (deviceIndex == paNoDevice) {
        throw SamplerException("Error: No default output device");
    }

    PaStreamParameters outputParameters;
    outputParameters.device = Pa_GetDefaultOutputDevice();
    outputParameters.channelCount = static_cast<int>(channels);
    outputParameters.sampleFormat = paFloat32;
    outputParameters.suggestedLatency = Pa_GetDeviceInfo(outputParameters.device)->defaultLowOutputLatency;
    outputParameters.hostApiSpecificStreamInfo = nullptr;

    PaStream *rawStream = nullptr;

    PaError err;
    if ((err = Pa_OpenStream(
            &rawStream,
            nullptr,
            &outputParameters,
            sampleRate,
            paFramesPerBufferUnspecified,
            paNoFlag,
            nullptr,
            nullptr
    )) != paNoError || !rawStream) {
        throw SamplerException(std::string(Pa_GetErrorText(err)));
    }

    stream = std::unique_ptr<PaStream, PaStreamDeleter>(rawStream);
}

void Sampler::setPlaybackSpeed(float factor) {
    std::shared_lock<std::shared_mutex> lock(mutex);

    if (factor < 0.5 || factor > 2) {
        throw SamplerException("Playback speed factor out of range (0.5, 2)");
    }

    playbackSpeedFactor = factor;
}

void Sampler::setVolume(float value) {
    std::shared_lock<std::shared_mutex> lock(mutex);

    if (value < 0 || value > 1) {
        throw SamplerException("Volume out of range (0, 1)");
    }

    volume = value;
}

int Sampler::start() {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!stretch || !stream) {
        throw SamplerException("Unable to start uninitialized sampler");
    }

    if (Pa_IsStreamActive(stream.get()) == 1) {
        throw SamplerException("Unable to start active sampler");
    }

    PaError err = Pa_StartStream(stream.get());

    if (err != paNoError) {
        throw SamplerException("Failed to start PortAudio stream: " + std::string(Pa_GetErrorText(err)));
    }

    double outputLatency = Pa_GetStreamInfo(stream.get())->outputLatency;

    double stretchInputLatency = stretch->inputLatency() / static_cast<double>(sampleRate);

    double stretchOutputLatency = stretch->outputLatency() / static_cast<double>(sampleRate);

    double totalLatency = outputLatency + stretchInputLatency + stretchOutputLatency;

    return static_cast<int>(totalLatency * 1'000'000);
}

void Sampler::write(const uint8_t *buffer, const uint64_t size) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!stretch || !stream || Pa_IsStreamActive(stream.get()) <= 0) {
        throw SamplerException("Unable to play uninitialized sampler");
    }

    if (!buffer || size <= 0) {
        throw SamplerException("Invalid buffer or size");
    }

    int inputSamples = static_cast<int>(static_cast<float>(size) / sizeof(float) / static_cast<float>(channels));

    int outputSamples = static_cast<int>(static_cast<float>(inputSamples) / playbackSpeedFactor);

    std::vector<std::vector<float>> inputBuffers(channels, std::vector<float>(inputSamples));

    std::vector<std::vector<float>> outputBuffers(channels, std::vector<float>(outputSamples));

    auto floatBuffer = reinterpret_cast<const float *>(buffer);

    for (int sample = 0; sample < inputSamples; ++sample) {
        for (int channel = 0; channel < channels; ++channel) {
            inputBuffers[channel][sample] = std::clamp(floatBuffer[sample * channels + channel], -1.0f, 1.0f);
        }
    }

    stretch->process(inputBuffers, inputSamples, outputBuffers, outputSamples);

    if (samples.size() < outputSamples * channels) {
        samples.resize(outputSamples * channels);
    }

    for (int sample = 0; sample < outputSamples; sample++) {
        for (int channel = 0; channel < channels; channel++) {
            samples[sample * channels + channel] = std::clamp(outputBuffers[channel][sample] * volume, -1.0f, 1.0f);
        }
    }

    Pa_WriteStream(stream.get(), samples.data(), samples.size() / channels);

    samples.clear();
}

void Sampler::stop() {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!stretch || !stream) {
        throw SamplerException("Unable to pause uninitialized sampler");
    }

    auto isStreamActive = Pa_IsStreamActive(stream.get());

    if (isStreamActive == 1) {
        PaError err = Pa_StopStream(stream.get());

        if (err != paNoError) {
            throw SamplerException("Failed to abort PortAudio stream: " + std::string(Pa_GetErrorText(err)));
        }
    } else if (isStreamActive < 0) {
        throw SamplerException("Failed to check stream status: " + std::string(Pa_GetErrorText(isStreamActive)));
    }
}

void Sampler::flush() {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!stretch || !stream) {
        throw SamplerException("Unable to stop uninitialized sampler");
    }

    auto isStreamActive = Pa_IsStreamActive(stream.get());

    if (isStreamActive == 1) {
        PaError err = Pa_AbortStream(stream.get());

        if (err != paNoError) {
            throw SamplerException("Failed to stop PortAudio stream: " + std::string(Pa_GetErrorText(err)));
        }
    }

    int outputSamples = stretch->outputLatency();

    if (outputSamples > 0) {
        std::vector<std::vector<float>> outputBuffers(channels, std::vector<float>(outputSamples, 0.0f));

        stretch->flush(outputBuffers, outputSamples);
    }

    stretch->reset();

    samples.clear();

    samples.shrink_to_fit();
}

void Sampler::drain() {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!stretch || !stream) {
        throw SamplerException("Unable to drain uninitialized sampler");
    }

    auto isStreamActive = Pa_IsStreamActive(stream.get());

    if (isStreamActive == 1) {
        PaError err = Pa_StopStream(stream.get());

        if (err != paNoError) {
            throw SamplerException("Failed to stop PortAudio stream: " + std::string(Pa_GetErrorText(err)));
        }
    }

    int outputSamples = stretch->outputLatency();

    if (outputSamples > 0) {
        std::vector<std::vector<float>> outputBuffers(channels, std::vector<float>(outputSamples, 0.0f));

        stretch->flush(outputBuffers, outputSamples);

        if (samples.size() < outputSamples * channels) {
            samples.resize(outputSamples * channels);
        }

        for (int sample = 0; sample < outputSamples; sample++) {
            for (int channel = 0; channel < channels; channel++) {
                samples[sample * channels + channel] = std::clamp(outputBuffers[channel][sample] * volume, -1.0f, 1.0f);
            }
        }

        Pa_WriteStream(stream.get(), samples.data(), samples.size() / channels);

        Pa_StopStream(stream.get());
    }

    stretch->reset();

    samples.clear();

    samples.shrink_to_fit();
}