package di

import hub.HubItemRepository
import hub.presentation.HubFeature
import hub.presentation.HubReducer
import io.github.numq.klarity.player.KlarityPlayer
import io.github.numq.klarity.probe.ProbeManager
import io.github.numq.klarity.queue.MediaQueue
import item.*
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
import preview.*
import probe.ProbeService
import renderer.RendererManager
import renderer.RendererRepository

private val HUB_SCOPE = Scope.HUB.getScopeName()

private val PLAYLIST_SCOPE = Scope.PLAYLIST.getScopeName()

private val probe = module {
    single { ProbeManager }

    single { ProbeService.Implementation(get()) } bind ProbeService::class
}

private val preview = module {
//    single { PreviewRepository.Implementation() } bind PreviewRepository::class onClose {
//        runBlocking {
//            it?.close()?.getOrThrow()
//        }
//    }

    single { PreviewService.Implementation() } bind PreviewService::class onClose {
        runBlocking {
            it?.close()?.getOrThrow()
        }
    }

    scope(PLAYLIST_SCOPE) {
        scoped { GetPreview(get(), get(), get()) }

        scoped { GetPreviewState(get()) }

        scoped { StartPreview(get(), get(), get()) }

        scoped { StopPreview(get()) }
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
    scope(HUB_SCOPE) {
        scoped { RendererRepository.Implementation() } bind RendererRepository::class onClose {
            runBlocking {
                it?.close()?.getOrThrow()
            }
        }

        scoped { RendererManager.Implementation(get(), get()) } bind RendererManager::class
    }

    scope(PLAYLIST_SCOPE) {
        scoped { RendererRepository.Implementation() } bind RendererRepository::class onClose {
            runBlocking {
                it?.close()?.getOrThrow()
            }
        }

        scoped { RendererManager.Implementation(get(), get()) } bind RendererManager::class
    }
}

private val hub = module {
    scope(HUB_SCOPE) {
        scoped { HubItemRepository() } bind ItemRepository::class

        scoped { HubReducer(get(), get(), get(), get(), get(), get(), get(), get(), get()) }

        scoped { HubFeature(get()) } onClose { it?.close() }
    }
}

private val item = module {
    scope(HUB_SCOPE) {
        scoped { GetItems(get()) }

        scoped { AddItem(get(), get(), get(), get(), get()) }

        scoped { RemoveItem(get()) }
    }

    scope(PLAYLIST_SCOPE) {
        scoped { GetItems(get()) }

        scoped { AddItem(get(), get(), get(), get(), get()) }

        scoped { RemoveItem(get()) }
    }
}

private val navigation = module {
    single { NavigationReducer() }

    single { NavigationFeature(get()) } onClose { it?.close() }
}

private val playback = module {
    scope(HUB_SCOPE) {
        scoped { PlaybackService.Implementation(get()) } bind PlaybackService::class

        scoped { ChangePlaybackSpeed(get()) }

        scoped { ChangeVolume(get()) }

        scoped { ControlPlayback(get()) }

        scoped { GetPlaybackState(get()) }

        scoped { ToggleMute(get()) }
    }

    scope(PLAYLIST_SCOPE) {
        scoped { PlaybackService.Implementation(get()) } bind PlaybackService::class

        scoped { ChangePlaybackSpeed(get()) }

        scoped { ChangeVolume(get()) }

        scoped { ControlPlayback(get()) }

        scoped { GetPlaybackState(get()) }

        scoped { ToggleMute(get()) }
    }
}

private val playlist = module {
    scope(PLAYLIST_SCOPE) {
        scoped { PlaylistService.Implementation(get()) } bind PlaylistService::class

        scoped { PlaylistItemRepository(get()) } bind ItemRepository::class

        scoped { SelectPlaylistItem(get()) }

        scoped { ChangePlaylistMode(get()) }

        scoped { ChangePlaylistShuffling(get()) }

        scoped { GetSelectedItem(get()) }

        scoped { GetPlaylist(get()) }

        scoped { PreviousPlaylistItem(get()) }

        scoped { NextPlaylistItem(get()) }

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

internal val appModule = listOf(probe, preview, player, renderer, hub, item, navigation, playback, playlist)