package preview

import decoder.Decoder
import frame.Frame
import media.Media

sealed interface InternalPreviewState {
    data object Empty : InternalPreviewState

    data class Ready(val decoder: Decoder<Media.Video, Frame.Video>) : InternalPreviewState
}