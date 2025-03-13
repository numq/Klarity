#ifndef KLARITY_SAMPLER_DELETER_H
#define KLARITY_SAMPLER_DELETER_H

#include "portaudio.h"
#include "stretch/stretch.h"

struct pa_stream_deleter {
    void operator()(PaStream *pa_stream) const {
        if (pa_stream) {
            Pa_CloseStream(pa_stream);
        }
    }
};

struct signalsmith_stretch_deleter {
    void operator()(signalsmith::stretch::SignalsmithStretch<float> *signalsmith_stretch) const {
        if (signalsmith_stretch) {
            signalsmith_stretch->reset();
        }
    }
};

#endif //KLARITY_SAMPLER_DELETER_H
