package di

import hub.*
import hub.presentation.*
import io.github.numq.klarity.player.KlarityPlayer
import io.github.numq.klarity.probe.ProbeManager
import io.github.numq.klarity.queue.MediaQueue
import io.github.numq.klarity.snapshot.SnapshotManager
import item.Item
import kotlinx.coroutines.runBlocking
import navigation.NavigationFeature
import navigation.NavigationReducer
import org.koin.core.component.getScopeName
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose
import playback.*
import playlist.*
import playlist.presentation.PlaylistFeature
import playlist.presentation.PlaylistReducer
import preview.LoopPreviewService
import preview.TimelinePreviewService
import renderer.RendererRegistry
import thumbnail.ThumbnailRepository

private val HUB_SCOPE = Scope.HUB.getScopeName()

private val PLAYLIST_SCOPE = Scope.PLAYLIST.getScopeName()

private val probe = module {
    single { ProbeManager }
}

private val snapshot = module {
    single { SnapshotManager }
}

private val preview = module {
    scope(HUB_SCOPE) {
        scoped { LoopPreviewService.Implementation(get()) } bind LoopPreviewService::class
    }

    scope(PLAYLIST_SCOPE) {
        scoped { TimelinePreviewService.Implementation(get()) } bind TimelinePreviewService::class
    }
}

private val player = module {
    scope(HUB_SCOPE) {
        scoped { KlarityPlayer.create().getOrThrow() }
    }

    scope(PLAYLIST_SCOPE) {
        scoped { KlarityPlayer.create().getOrThrow() }

        scoped { MediaQueue.create<Item>().getOrThrow() }
    }
}

private val renderer = module {
    single { RendererRegistry.Implementation() } bind RendererRegistry::class onClose {
        runBlocking {
            it?.close()?.getOrThrow()
        }
    }

    single { (id: String) -> runBlocking { get<RendererRegistry>().get(id = id) } }
}

private val hub = module {
    scope(HUB_SCOPE) {
        scoped { HubRepository.Implementation(get(), get(), get()) } bind HubRepository::class

        scoped { GetHub(get()) }

        scoped { AddHubItem(get(), get()) }

        scoped { RemoveHubItem(get(), get()) }

        scoped { StartHubPlayback(get(), get()) }

        scoped { StartHubPreview(get()) }

        scoped { StopHubPlayback(get()) }

        scoped { StopHubPreview(get()) }

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
    scope(HUB_SCOPE) {
        scoped { PlaybackService.Implementation(get(), get()) } bind PlaybackService::class onClose {
            runBlocking {
                it?.close()?.getOrThrow()
            }
        }

        scoped { ChangePlaybackSpeed(get()) }

        scoped { ChangeVolume(get()) }

        scoped { ToggleMute(get()) }
    }

    scope(PLAYLIST_SCOPE) {
        scoped { PlaybackService.Implementation(get(), get()) } bind PlaybackService::class onClose {
            runBlocking {
                it?.close()?.getOrThrow()
            }
        }

        scoped { ChangePlaybackSpeed(get()) }

        scoped { ChangeVolume(get()) }

        scoped { GetPlaybackState(get(), get()) }

        scoped { ToggleMute(get()) }
    }
}

private val playlist = module {
    scope(PLAYLIST_SCOPE) {
        scoped { PlaylistService.Implementation(get()) } bind PlaylistService::class

        scoped { PlaylistRepository.Implementation(get(), get()) } bind PlaylistRepository::class

        scoped { GetPlaylist(get()) }

        scoped { AddPlaylistItem(get()) }

        scoped { RemovePlaylistItem(get(), get()) }

        scoped { SelectPlaylistItem(get()) }

        scoped { ChangePlaylistMode(get()) }

        scoped { ChangePlaylistShuffling(get()) }

        scoped { PreviousPlaylistItem(get()) }

        scoped { NextPlaylistItem(get()) }

        scoped { ControlPlaylistPlayback(get()) }

        scoped {
            PlaylistReducer(
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get(),
                get()
            )
        }

        scoped { PlaylistFeature(get()) } onClose { it?.close() }
    }
}

private val thumbnail = module {
    single { ThumbnailRepository.Implementation(get()) } bind ThumbnailRepository::class onClose {
        runBlocking {
            it?.close()?.getOrThrow()
        }
    }
}

internal val appModule = listOf(probe, preview, player, renderer, hub, navigation, playback, playlist, thumbnail)