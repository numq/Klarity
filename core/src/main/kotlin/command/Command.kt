package command

sealed interface Command {
    data class Prepare(val location: String, val audioBufferSize: Int, val videoBufferSize: Int) : Command

    data object Play : Command

    data object Pause : Command

    data object Resume : Command

    data object Stop : Command

    data class SeekTo(val millis: Long) : Command

    data object Release : Command
}