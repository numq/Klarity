package controller.player

import controller.stateless.StatelessController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import media.Media
import playback.PlaybackState
import playback.PlaybackStatus
import playlist.Playlist
import sink.RenderSink

interface PlayerController : AutoCloseable {
    companion object {
        fun create(): PlayerController = DefaultPlayerController(StatelessController.create(RenderSink.create()))
    }

    val controller: StatelessController
    val renderSink: RenderSink
    val state: StateFlow<PlaybackState>
    val status: StateFlow<PlaybackStatus>
    val exception: Flow<Exception>
    suspend fun attachPlaylist(playlist: Playlist)
    suspend fun detachPlaylist()
    suspend fun changeRemoteBufferSizeFactor(value: Int)
    suspend fun snapshot(timestampMillis: Long): ByteArray?
    suspend fun toggleMute()
    suspend fun changeVolume(value: Float)
    suspend fun load(media: Media)
    suspend fun unload()
    suspend fun play()
    suspend fun pause()
    suspend fun stop()
    suspend fun seekTo(timestampMillis: Long)
}