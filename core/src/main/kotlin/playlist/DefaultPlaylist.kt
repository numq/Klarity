package playlist

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import media.Media

internal class DefaultPlaylist(
    initialShuffled: Boolean,
    initialRepeatMode: Playlist.RepeatMode,
) : Playlist {

    private val _events = Channel<PlaylistEvent>(Channel.BUFFERED)

    override val events = _events.consumeAsFlow()

    private val _playingMedia = MutableStateFlow<PlaylistMedia?>(null)

    override val playingMedia = _playingMedia.asStateFlow()

    private val _queue = MutableStateFlow(emptyList<PlaylistMedia>())

    override val queue = _queue.asStateFlow()

    private val _shuffled = MutableStateFlow(initialShuffled)

    override val shuffled = _shuffled.asStateFlow()

    private val _repeatMode = MutableStateFlow(initialRepeatMode)

    override val repeatMode = _repeatMode.asStateFlow()

    override fun hasPrevious() = shuffled.value
            || repeatMode.value in arrayOf(Playlist.RepeatMode.SINGLE, Playlist.RepeatMode.PLAYLIST)
            || queue.value.indexOf(playingMedia.value).let { index -> index != -1 && index > 0 }

    override fun hasNext() = shuffled.value
            || repeatMode.value in arrayOf(Playlist.RepeatMode.SINGLE, Playlist.RepeatMode.PLAYLIST)
            || queue.value.indexOf(playingMedia.value).let { index -> index != -1 && index < queue.value.lastIndex }

    override suspend fun toggleShuffle() {
        _shuffled.emit(!shuffled.value)

        _queue.emit(
            if (shuffled.value) queue.value.shuffled()
            else queue.value.sortedBy(PlaylistMedia::addedAtMillis)
        )

        _events.send(PlaylistEvent.Shuffle)
    }

    override suspend fun changeRepeatMode(repeatMode: Playlist.RepeatMode) {
        _repeatMode.emit(repeatMode)

        _events.send(PlaylistEvent.ChangeRepeatMode(repeatMode))
    }

    override suspend fun add(media: Media) {
        if (queue.value.none { it.media == media }) {
            val playlistMedia = PlaylistMedia(media = media, addedAtMillis = System.currentTimeMillis())

            _queue.emit(
                queue.value.plus(playlistMedia).sortedBy(PlaylistMedia::addedAtMillis)
            )

            _events.send(PlaylistEvent.Add(playlistMedia))
        }
    }

    override suspend fun remove(playlistMedia: PlaylistMedia) {
        val index = queue.value.indexOf(playlistMedia)

        _queue.emit(queue.value.filterNot { it == playlistMedia })

        if (playlistMedia == playingMedia.value) _playingMedia.emit(
            queue.value.getOrNull(
                index.coerceAtMost(queue.value.lastIndex)
            )
        )
    }

    override suspend fun select(playlistMedia: PlaylistMedia) {
        _playingMedia.emit(playlistMedia)

        _events.send(PlaylistEvent.Select(playlistMedia))
    }

    override suspend fun previous() {
        if (repeatMode.value == Playlist.RepeatMode.SINGLE) _repeatMode.emit(Playlist.RepeatMode.PLAYLIST)

        val playlistMedia = when {
            queue.value.isEmpty() -> null

            shuffled.value -> queue.value.filterNot { media -> media == playingMedia.value }.randomOrNull()

            repeatMode.value == Playlist.RepeatMode.NONE -> queue.value.getOrNull(
                (queue.value.indexOf(playingMedia.value) - 1).coerceAtLeast(
                    0
                )
            )

            else -> {
                val index = queue.value.indexOf(playingMedia.value)
                queue.value.getOrNull((index - 1 + queue.value.size) % queue.value.size)
            }
        }

        _playingMedia.emit(playlistMedia?.apply {
            _events.send(PlaylistEvent.Previous(playlistMedia))
        })
    }

    override suspend fun next() {
        if (repeatMode.value == Playlist.RepeatMode.SINGLE) _repeatMode.emit(Playlist.RepeatMode.PLAYLIST)

        val playlistMedia = when {
            queue.value.isEmpty() -> null

            shuffled.value -> queue.value.filterNot { media -> media == playingMedia.value }.randomOrNull()

            repeatMode.value == Playlist.RepeatMode.NONE -> queue.value.getOrNull(
                (queue.value.indexOf(playingMedia.value) + 1).coerceAtMost(
                    queue.value.lastIndex
                )
            )

            else -> {
                val index = queue.value.indexOf(playingMedia.value)
                queue.value.getOrNull((index + 1) % queue.value.size)
            }
        }

        _playingMedia.emit(playlistMedia?.apply {
            _events.send(PlaylistEvent.Next(playlistMedia))
        })
    }
}