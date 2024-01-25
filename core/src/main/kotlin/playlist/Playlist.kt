package playlist

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import media.Media

/**
 * Interface representing a media playlist with basic operations and state information.
 *
 * This interface provides access to various aspects of a playlist, including the current media,
 * shuffle and repeat modes, and the ability to perform common playlist operations.
 *
 * @see PlaylistEvent for events emitted by the playlist.
 */
interface Playlist : AutoCloseable {
    /**
     * Factory companion object to create instances of the Playlist interface.
     */
    companion object {
        /**
         * Creates a new playlist with the specified initial settings.
         *
         * @param initialShuffled Initial shuffle mode.
         * @param initialRepeatMode Initial repeat mode.
         * @return A new instance of the Playlist interface.
         */
        fun create(
            initialShuffled: Boolean = false,
            initialRepeatMode: RepeatMode = RepeatMode.NONE,
        ): Playlist = DefaultPlaylist(
            initialShuffled = initialShuffled,
            initialRepeatMode = initialRepeatMode
        )
    }

    /**
     * Flow representing events in the playlist, such as media additions, removals, etc.
     */
    val event: Flow<PlaylistEvent>

    /**
     * State flow representing the currently playing media in the playlist.
     */
    val playingMedia: StateFlow<PlaylistMedia?>

    /**
     * State flow representing the current queue of media in the playlist.
     */
    val queue: StateFlow<List<PlaylistMedia>>

    /**
     * State flow representing whether the playlist is currently shuffled.
     */
    val shuffled: StateFlow<Boolean>

    /**
     * State flow representing the current repeat mode of the playlist.
     */
    val repeatMode: StateFlow<RepeatMode>

    /**
     * State flow representing whether there is a previous media item in the playlist.
     */
    val hasPrevious: StateFlow<Boolean>

    /**
     * State flow representing whether there is a next media item in the playlist.
     */
    val hasNext: StateFlow<Boolean>

    /**
     * Toggles the shuffle mode of the playlist.
     */
    suspend fun toggleShuffle()

    /**
     * Changes the repeat mode of the playlist.
     *
     * @param repeatMode The new repeat mode.
     */
    suspend fun changeRepeatMode(repeatMode: RepeatMode)

    /**
     * Adds a media item to the playlist.
     *
     * @param media The media item to add.
     */
    suspend fun add(media: Media)

    /**
     * Removes a media item from the playlist.
     *
     * @param playlistMedia The media item to remove.
     */
    suspend fun remove(playlistMedia: PlaylistMedia)

    /**
     * Selects a media item in the playlist for playback.
     *
     * @param playlistMedia The media item to select.
     */
    suspend fun select(playlistMedia: PlaylistMedia)

    /**
     * Moves to the previous media item in the playlist.
     */
    suspend fun previous()

    /**
     * Moves to the next media item in the playlist.
     */
    suspend fun next()

    /**
     * Enumeration representing the repeat modes of the playlist.
     */
    enum class RepeatMode {
        /**
         * No repeat. Play each item once.
         */
        NONE,

        /**
         * Repeat the current item.
         */
        SINGLE,

        /**
         * Repeat the entire playlist.
         */
        PLAYLIST
    }
}
