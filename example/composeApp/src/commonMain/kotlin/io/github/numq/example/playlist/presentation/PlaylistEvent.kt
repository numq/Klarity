package io.github.numq.example.playlist.presentation

import io.github.numq.example.event.Event
import kotlinx.coroutines.flow.Flow
import io.github.numq.example.playlist.Playlist
import java.util.*

sealed class PlaylistEvent private constructor() : Event<UUID> {
    override val key: UUID = UUID.randomUUID()

    data class Error(val message: String) : PlaylistEvent()

    data class HandlePlaylist(val playlist: Flow<Playlist>) : PlaylistEvent()
}