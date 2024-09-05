package preview

import media.Media

sealed interface PreviewState {
    data object Empty : PreviewState

    data class Ready(val media: Media.Video) : PreviewState
}