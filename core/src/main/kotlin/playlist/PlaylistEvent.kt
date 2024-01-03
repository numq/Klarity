package playlist

sealed class PlaylistEvent private constructor() {
    data class Add(val playlistMedia: PlaylistMedia) : PlaylistEvent()
    data class Remove(val removedPlaylistMedia: PlaylistMedia, val nextPlaylistMedia: PlaylistMedia?) : PlaylistEvent()
    data class Select(val playlistMedia: PlaylistMedia) : PlaylistEvent()
    data class Previous(val playlistMedia: PlaylistMedia?) : PlaylistEvent()
    data class Next(val playlistMedia: PlaylistMedia?) : PlaylistEvent()
}