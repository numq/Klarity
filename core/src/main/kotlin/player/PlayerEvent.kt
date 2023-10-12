package player

import media.MediaSettings

sealed class PlayerEvent private constructor() {
    data class Load(val settings: MediaSettings) : PlayerEvent()
    object Play : PlayerEvent()
    object Pause : PlayerEvent()
    object Stop : PlayerEvent()
    object Complete : PlayerEvent()
    data class SeekTo(val timestampMillis: Long) : PlayerEvent()
}