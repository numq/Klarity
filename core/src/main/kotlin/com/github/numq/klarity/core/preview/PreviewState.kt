package com.github.numq.klarity.core.preview

import com.github.numq.klarity.core.media.Media

sealed interface PreviewState {
    data object Empty : PreviewState

    data class Ready(val media: Media.Video) : PreviewState
}