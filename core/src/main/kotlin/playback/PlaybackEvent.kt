package playback

import media.Media

sealed class PlaybackEvent private constructor() {
    data class Load(val media: Media) : PlaybackEvent()

    object Unload : PlaybackEvent()

    object Play : PlaybackEvent()

    object Resume : PlaybackEvent()

    object Pause : PlaybackEvent()

    object Stop : PlaybackEvent()

    object EndOfMedia : PlaybackEvent()

    object SeekStarted : PlaybackEvent()

    data class SeekEnded(val timestampNanos: Long) : PlaybackEvent()
}