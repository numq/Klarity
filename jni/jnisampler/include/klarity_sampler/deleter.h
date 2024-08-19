#ifndef KLARITY_SAMPLER_DELETER_H
#define KLARITY_SAMPLER_DELETER_H

#include "portaudio.h"
#include "stretch/stretch.h"

struct PaStreamDeleter {
    void operator()(PaStream *stream) const {
        if (stream) {
            Pa_AbortStream(stream);
            Pa_CloseStream(stream);
        }
    }
};

struct SignalsmithStretchDeleter {
    void operator()(signalsmith::stretch::SignalsmithStretch<float> *stretch) const {
        if (stretch) {
            stretch->reset();
        }
    }
};

#endif //KLARITY_SAMPLER_DELETER_H
