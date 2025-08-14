package io.github.numq.example.playlist

import io.github.numq.example.playback.PlaybackService
import io.github.numq.example.playback.PlaybackState
import io.github.numq.example.preview.TimelinePreviewService
import io.github.numq.example.renderer.RendererService
import io.github.numq.example.usecase.UseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

class GetPlaylist(
    private val playlistRepository: PlaylistRepository,
    private val playlistService: PlaylistService,
    private val playbackService: PlaybackService,
    private val rendererService: RendererService,
    private val timelinePreviewService: TimelinePreviewService,
) : UseCase<Unit, Flow<Playlist>> {
    override suspend fun execute(input: Unit) = runCatching {
        playlistRepository.playlist.onEach { playlist ->
            val release = suspend {
                playbackService.release().getOrThrow()

                playbackService.detachRenderer().getOrThrow()

                rendererService.remove(id = "playback").getOrThrow()

                timelinePreviewService.release().getOrThrow()

                rendererService.remove(id = "preview").getOrThrow()
            }

            when (val selectedPlaylistItem = playlist.selectedPlaylistItem) {
                is SelectedPlaylistItem.Absent -> {
                    release()

                    playlistRepository.updatePreviousSelectedPlaylistItem(selectedPlaylistItem = null).getOrThrow()
                }

                is SelectedPlaylistItem.Present -> {
                    if (selectedPlaylistItem != playlistRepository.previousSelectedPlaylistItem.value) {
                        release()

                        playbackService.prepare(location = selectedPlaylistItem.item.location).getOrThrow()

                        rendererService.add(id = "playback", location = selectedPlaylistItem.item.location).getOrThrow()

                        rendererService.reset(id = "playback").getOrThrow()

                        playbackService.attachRenderer(id = "playback").getOrThrow()

                        timelinePreviewService.prepare(item = selectedPlaylistItem.item).getOrThrow()

                        rendererService.add(id = "preview", location = selectedPlaylistItem.item.location).getOrThrow()

                        playbackService.prepare(location = selectedPlaylistItem.item.location).getOrThrow()

                        playbackService.play().getOrThrow()

                        playlistRepository.updatePreviousSelectedPlaylistItem(
                            selectedPlaylistItem = selectedPlaylistItem
                        ).getOrThrow()
                    }
                }
            }

            if (playlist.playbackState is PlaybackState.Ready.Completed) {
                playlistService.next().getOrThrow()
            }
        }
    }
}