package playlist

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import media.Media

internal class DefaultPlaylist(
    initialShuffled: Boolean,
    initialRepeatMode: Playlist.RepeatMode,
) : Playlist {

    private val coroutineContext = Dispatchers.Default + SupervisorJob()

    private val coroutineScope = CoroutineScope(coroutineContext)

    private val _event = MutableSharedFlow<PlaylistEvent>()

    override val event = _event.asSharedFlow()

    private val _playingMedia = MutableStateFlow<PlaylistMedia?>(null)

    override val playingMedia = _playingMedia.asStateFlow()

    private val _queue = MutableStateFlow(emptyList<PlaylistMedia>())

    override val queue = _queue.asStateFlow()

    private val _shuffled = MutableStateFlow(initialShuffled)

    override val shuffled = _shuffled.asStateFlow()

    private val _repeatMode = MutableStateFlow(initialRepeatMode)

    override val repeatMode = _repeatMode.asStateFlow()

    private val _hasPrevious = MutableStateFlow(false)

    override val hasPrevious = _hasPrevious.asStateFlow()

    private val _hasNext = MutableStateFlow(false)

    override val hasNext = _hasNext.asStateFlow()

    private fun hasPrevious() = shuffled.value
            || repeatMode.value in arrayOf(Playlist.RepeatMode.SINGLE, Playlist.RepeatMode.PLAYLIST)
            || queue.value.indexOf(playingMedia.value).let { index -> index != -1 && index > 0 }

    private fun hasNext() = shuffled.value
            || repeatMode.value in arrayOf(Playlist.RepeatMode.SINGLE, Playlist.RepeatMode.PLAYLIST)
            || queue.value.indexOf(playingMedia.value).let { index -> index != -1 && index < queue.value.lastIndex }

    override suspend fun toggleShuffle() {
        _shuffled.emit(!shuffled.value)

        _queue.emit(
            if (shuffled.value) queue.value.shuffled()
            else queue.value.sortedBy(PlaylistMedia::addedAtMillis)
        )
    }

    override suspend fun changeRepeatMode(repeatMode: Playlist.RepeatMode) {
        _repeatMode.emit(repeatMode)
    }

    override suspend fun add(media: Media) {
        if (queue.value.none { it.media == media }) {
            val playlistMedia = PlaylistMedia(media = media, addedAtMillis = System.currentTimeMillis())

            _queue.emit(
                queue.value.plus(playlistMedia).sortedBy(PlaylistMedia::addedAtMillis)
            )

            _event.emit(PlaylistEvent.Add(playlistMedia))
        }
    }

    override suspend fun remove(playlistMedia: PlaylistMedia) {
        if (queue.value.contains(playlistMedia)) {
            val index = queue.value.indexOf(playlistMedia)

            _queue.emit(queue.value.filterNot { it == playlistMedia })

            val nextMedia =
                if (playingMedia.value == playlistMedia) queue.value.getOrNull(index.coerceAtMost(queue.value.lastIndex)) else null

            _playingMedia.emit(nextMedia)

            _event.emit(PlaylistEvent.Remove(playlistMedia, nextMedia))
        }
    }

    override suspend fun select(playlistMedia: PlaylistMedia) {
        if (queue.value.contains(playlistMedia)) {
            _playingMedia.emit(playlistMedia)

            _event.emit(PlaylistEvent.Select(playlistMedia))
        }
    }

    override suspend fun previous() {
        when {
            queue.value.isEmpty() -> null

            shuffled.value -> queue.value.filterNot { media -> media == playingMedia.value }.randomOrNull()

            repeatMode.value == Playlist.RepeatMode.SINGLE -> playingMedia.value

            repeatMode.value == Playlist.RepeatMode.NONE -> queue.value.getOrNull(
                (queue.value.indexOf(playingMedia.value) - 1).coerceAtLeast(0)
            )

            else -> {
                val index = queue.value.indexOf(playingMedia.value)
                queue.value.getOrNull((index - 1 + queue.value.size) % queue.value.size)
            }
        }.let { playlistMedia ->
            _playingMedia.emit(playlistMedia)

            _event.emit(PlaylistEvent.Previous(playlistMedia))
        }
    }

    override suspend fun next() {
        when {
            queue.value.isEmpty() -> null

            shuffled.value -> queue.value.filterNot { media -> media == playingMedia.value }.randomOrNull()

            repeatMode.value == Playlist.RepeatMode.SINGLE -> playingMedia.value

            repeatMode.value == Playlist.RepeatMode.NONE -> queue.value.getOrNull(
                (queue.value.indexOf(playingMedia.value) + 1).coerceAtMost(
                    queue.value.lastIndex
                )
            )

            else -> {
                val index = queue.value.indexOf(playingMedia.value)
                queue.value.getOrNull((index + 1) % queue.value.size)
            }
        }.let { playlistMedia ->
            _playingMedia.emit(playlistMedia)

            _event.emit(PlaylistEvent.Next(playlistMedia))
        }
    }

    override fun close() = coroutineScope.cancel()

    init {
        merge(event, playingMedia, queue, shuffled, repeatMode).onEach {
            _hasPrevious.emit(hasPrevious())
            _hasNext.emit(hasNext())
        }.launchIn(coroutineScope)
    }
}