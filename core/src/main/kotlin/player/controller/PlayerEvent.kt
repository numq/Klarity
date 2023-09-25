package player.controller

sealed class PlayerEvent private constructor() {
    object Play : PlayerEvent()
    object Pause : PlayerEvent()
    object Stop : PlayerEvent()
    object Complete : PlayerEvent()
    data class SeekTo(val timestampMillis: Long) : PlayerEvent()
}