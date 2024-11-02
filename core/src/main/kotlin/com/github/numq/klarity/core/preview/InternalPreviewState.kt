package com.github.numq.klarity.core.preview

import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media

sealed interface InternalPreviewState {
    data object Empty : InternalPreviewState

    data class Ready(val decoder: Decoder<Media.Video, Frame.Video>) : InternalPreviewState
}