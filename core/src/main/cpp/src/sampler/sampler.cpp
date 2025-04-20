#include "sampler.h"

Sampler::Sampler(uint32_t sampleRate, uint32_t channels) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    this->sampleRate = sampleRate;

    this->channels = channels;

    PaDeviceIndex deviceIndex = Pa_GetDefaultOutputDevice();
    if (deviceIndex == paNoDevice) {
        throw SamplerException("Error: No default output device");
    }

    PaStreamParameters outputParameters;
    outputParameters.device = deviceIndex;
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
    ) != paNoError) || !rawStream) {
        throw SamplerException(std::string(Pa_GetErrorText(err)));
    }

    stream = std::unique_ptr<PaStream, PaStreamDeleter>(rawStream);

    stretch = std::make_unique<signalsmith::stretch::SignalsmithStretch<float>>();

    stretch->presetDefault(static_cast<int>(channels), static_cast<float>(sampleRate));
}

void Sampler::setPlaybackSpeed(float factor) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    playbackSpeedFactor = factor;
}

void Sampler::setVolume(float value) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    volume = value;
}

int Sampler::start() {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!stretch || stream == nullptr) {
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

    double stretchLatency =
            (stretch->inputLatency() + stretch->outputLatency()) / static_cast<double>(this->sampleRate);

    return static_cast<int>((outputLatency + stretchLatency) * 1'000'000);
}

void Sampler::play(const uint8_t *samples, uint64_t size) {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!stretch || stream == nullptr || Pa_IsStreamActive(stream.get()) <= 0) {
        throw SamplerException("Unable to play uninitialized sampler");
    }

    if (size <= 0) {
        throw SamplerException("Unable to play empty samples");
    }

    int inputSamples = static_cast<int>((float) size / sizeof(float) / (float) channels);

    int outputSamples = static_cast<int>((float) inputSamples / playbackSpeedFactor);

    std::vector<std::vector<float>> inputBuffers(channels, std::vector<float>(inputSamples));

    std::vector<std::vector<float>> outputBuffers(channels, std::vector<float>(outputSamples));

    for (int i = 0; i < inputSamples * channels; ++i) {
        inputBuffers[i % channels][i / channels] = reinterpret_cast<const float *>(samples)[i];
    }

    stretch->process(inputBuffers, inputSamples, outputBuffers, outputSamples);

    std::vector<float> output;

    output.reserve(outputSamples * channels);

    for (int i = 0; i < outputSamples; ++i) {
        for (int ch = 0; ch < channels; ++ch) {
            output.push_back(std::clamp(outputBuffers[ch][i] * volume, -1.0f, 1.0f));
        }
    }

    Pa_WriteStream(stream.get(), output.data(), outputSamples);
}

void Sampler::pause() {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!stretch || stream == nullptr) {
        throw SamplerException("Unable to pause uninitialized sampler");
    }

    auto isStreamActive = Pa_IsStreamActive(stream.get());

    if (isStreamActive == 1) {
        PaError err = Pa_AbortStream(stream.get());

        if (err != paNoError) {
            throw SamplerException("Failed to stop PortAudio stream: " + std::string(Pa_GetErrorText(err)));
        }
    } else if (isStreamActive < 0) {
        throw SamplerException("Failed to check stream status: " + std::string(Pa_GetErrorText(isStreamActive)));
    }
}

void Sampler::stop() {
    std::unique_lock<std::shared_mutex> lock(mutex);

    if (!stretch || stream == nullptr) {
        throw SamplerException("Unable to stop uninitialized sampler");
    }

    auto isStreamActive = Pa_IsStreamActive(stream.get());

    if (isStreamActive == 1) {
        PaError err = Pa_StopStream(stream.get());

        if (err != paNoError) {
            throw SamplerException("Failed to abort PortAudio stream: " + std::string(Pa_GetErrorText(err)));
        }
    }

    if (stretch) {
        int outputSamples = stretch->outputLatency();

        if (outputSamples > 0) {
            std::vector<std::vector<float>> outputBuffers(channels, std::vector<float>(outputSamples, 0.0f));

            stretch->flush(outputBuffers, outputSamples);
        }

        stretch->reset();
    }
}