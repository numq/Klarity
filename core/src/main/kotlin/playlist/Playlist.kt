package playlist

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import media.Media

interface Playlist {

    val events: Flow<PlaylistEvent>
    val playingMedia: StateFlow<PlaylistMedia?>
    val queue: StateFlow<List<PlaylistMedia>>
    val shuffled: StateFlow<Boolean>
    val repeatMode: StateFlow<RepeatMode>
    fun hasPrevious(): Boolean
    fun hasNext(): Boolean
    suspend fun toggleShuffle()
    suspend fun changeRepeatMode(repeatMode: RepeatMode)
    suspend fun add(media: Media)
    suspend fun remove(playlistMedia: PlaylistMedia)
    suspend fun select(playlistMedia: PlaylistMedia)
    suspend fun previous()
    suspend fun next()

    enum class RepeatMode {
        NONE, SINGLE, PLAYLIST
    }

    companion object {
        fun create(
            initialShuffled: Boolean = false,
            initialRepeatMode: RepeatMode = RepeatMode.NONE,
        ): Playlist = DefaultPlaylist(
            initialShuffled = initialShuffled,
            initialRepeatMode = initialRepeatMode
        )
    }
}