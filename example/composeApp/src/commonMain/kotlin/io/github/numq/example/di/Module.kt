package io.github.numq.example.di

import io.github.numq.example.hub.*
import io.github.numq.example.hub.presentation.*
import io.github.numq.example.item.Item
import io.github.numq.example.navigation.NavigationFeature
import io.github.numq.example.navigation.NavigationReducer
import io.github.numq.example.playback.ChangePlaybackSpeed
import io.github.numq.example.playback.ChangeVolume
import io.github.numq.example.playback.PlaybackService
import io.github.numq.example.playback.ToggleMute
import io.github.numq.example.playlist.*
import io.github.numq.example.playlist.presentation.*
import io.github.numq.example.preview.LoopPreviewService
import io.github.numq.example.preview.TimelinePreviewService
import io.github.numq.example.renderer.RendererRegistry
import io.github.numq.example.renderer.RendererService
import io.github.numq.klarity.player.KlarityPlayer
import io.github.numq.klarity.probe.ProbeManager
import io.github.numq.klarity.queue.MediaQueue
import io.github.numq.klarity.snapshot.SnapshotManager
import kotlinx.coroutines.runBlocking
import org.koin.core.component.getScopeName
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose

private val HUB_SCOPE = Scope.HUB.getScopeName()

private val PLAYLIST_SCOPE = Scope.PLAYLIST.getScopeName()

private val application = module {
    Scope.entries.forEach { scope ->
        scope(scope.getScopeName()) {
            scoped { ProbeManager } bind ProbeManager::class

            scoped { SnapshotManager } bind SnapshotManager::class
        }
    }

    scope(HUB_SCOPE) {
        scoped { KlarityPlayer.create().getOrThrow() } bind KlarityPlayer::class onClose {
            runBlocking {
                it?.close()?.getOrNull()
            }
        }
    }

    scope(PLAYLIST_SCOPE) {
        scoped { KlarityPlayer.create().getOrThrow() } bind KlarityPlayer::class onClose {
            runBlocking {
                it?.close()?.getOrNull()
            }
        }

        scoped { MediaQueue.create<Item, Item.Loaded>().getOrThrow() } bind MediaQueue::class
    }
}

private val preview = module {
    scope(HUB_SCOPE) {
        scoped { LoopPreviewService.Implementation(get()) } bind LoopPreviewService::class onClose {
            runBlocking {
                it?.close()?.getOrNull()
            }
        }
    }

    scope(PLAYLIST_SCOPE) {
        scoped { TimelinePreviewService.Implementation(get()) } bind TimelinePreviewService::class onClose {
            runBlocking {
                it?.close()?.getOrNull()
            }
        }
    }
}


private val renderer = module {
    Scope.entries.forEach { scope ->
        scope(scope.getScopeName()) {
            scoped { RendererRegistry.Implementation(get()) } bind RendererRegistry::class onClose {
                runBlocking {
                    it?.close()?.getOrNull()
                }
            }

            scoped { RendererService.Implementation(get()) } bind RendererService::class
        }
    }
}

private val hub = module {
    scope(HUB_SCOPE) {
        scoped { HubRepository.Implementation(get()) } bind HubRepository::class

        scoped { GetHub(get(), get(), get()) }

        scoped { AddHubItem(get(), get(), get()) }

        scoped { RemoveHubItem(get(), get()) }

        scoped { StartHubPlayback(get(), get(), get(), get()) }

        scoped { StartHubPreview(get(), get()) }

        scoped { StopHubPlayback(get(), get(), get()) }

        scoped { StopHubPreview(get(), get(), get()) }

        scoped { HubInteractionReducer() }

        scoped { HubPlaybackReducer(get(), get(), get()) }

        scoped { HubPreviewReducer(get(), get()) }

        scoped { HubReducer(get(), get(), get(), get(), get(), get()) }

        scoped { HubFeature(get()) } onClose { it?.close() }
    }
}

private val navigation = module {
    single { NavigationReducer() }

    single { NavigationFeature(get()) } onClose { it?.close() }
}

private val playback = module {
    Scope.entries.forEach { scope ->
        scope(scope.getScopeName()) {
            scoped { PlaybackService.Implementation(get(), get(), get()) } bind PlaybackService::class onClose {
                runBlocking {
                    it?.close()?.getOrNull()
                }
            }

            scoped { ChangePlaybackSpeed(get()) }

            scoped { ChangeVolume(get()) }

            scoped { ToggleMute(get()) }
        }
    }
}

private val playlist = module {
    scope(PLAYLIST_SCOPE) {
        scoped { PlaylistService.Implementation(get()) } bind PlaylistService::class

        scoped { PlaylistRepository.Implementation(get(), get()) } bind PlaylistRepository::class

        scoped { GetPlaylist(get(), get(), get(), get(), get()) }

        scoped { AddPlaylistItem(get(), get(), get()) }

        scoped { RemovePlaylistItem(get()) }

        scoped { SelectPlaylistItem(get()) }

        scoped { ChangePlaylistMode(get()) }

        scoped { ChangePlaylistShuffling(get()) }

        scoped { PreviousPlaylistItem(get()) }

        scoped { NextPlaylistItem(get()) }

        scoped { ControlPlaylistPlayback(get()) }

        scoped { GetTimelinePreview(get()) }

        scoped { PlaylistInteractionReducer() }

        scoped { PlaylistPlaybackReducer(get(), get(), get(), get()) }

        scoped { PlaylistPreviewReducer(get()) }

        scoped {
            PlaylistReducer(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
        }

        scoped { PlaylistFeature(get()) } onClose { it?.close() }
    }
}

internal val appModule = listOf(application, preview, renderer, hub, navigation, playback, playlist)