package playlist

sealed class PlaylistEvent private constructor() {
    object Shuffle : PlaylistEvent()
    data class ChangeRepeatMode(val repeatMode: Playlist.RepeatMode) : PlaylistEvent()
    data class Add(val playlistMedia: PlaylistMedia) : PlaylistEvent()
    data class Select(val playlistMedia: PlaylistMedia) : PlaylistEvent()
    data class Previous(val playlistMedia: PlaylistMedia) : PlaylistEvent()
    data class Next(val playlistMedia: PlaylistMedia) : PlaylistEvent()
}