package di

import hub.HubFeature
import hub.HubItemRepository
import hub.HubReducer
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
import playlist.PlaylistItemRepository
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
    single { PreviewRepository.Implementation() } bind PreviewRepository::class onClose {
        runBlocking {
            it?.close()?.getOrThrow()
        }
    }

    single { PreviewService.Implementation() } bind PreviewService::class onClose {
        runBlocking {
            it?.close()?.getOrThrow()
        }
    }

    scope(PLAYLIST_SCOPE) {
        scoped { StartPreview(get(), get(), get()) }

        scoped { StopPreview(get()) }

        scoped { ResetPreview(get(), get(), get()) }
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

private val navigation = module {
    single { NavigationReducer() }

    single { NavigationFeature(get()) } onClose { it?.close() }
}

private val item = module {
    scope(HUB_SCOPE) {
        scoped { GetItems(get()) }

        scoped { AddItem(get(), get(), get(), get(), get()) }

        scoped { RemoveItem(get(), get(), get()) }
    }

    scope(PLAYLIST_SCOPE) {
        scoped { GetItems(get()) }

        scoped { AddItem(get(), get(), get(), get(), get()) }

        scoped { RemoveItem(get(), get(), get()) }
    }
}

private val playback = module {
    scope(HUB_SCOPE) {
        scoped { PlaybackService.Implementation(get()) } bind PlaybackService::class

        scoped { GetPlaybackItem(get(), get()) }

        scoped { ToggleMute(get()) }

        scoped { ChangeVolume(get()) }

        scoped { ChangePlaybackSpeed(get()) }

        scoped { ControlPlayback(get()) }
    }

    scope(PLAYLIST_SCOPE) {
        scoped { PlaybackService.Implementation(get()) } bind PlaybackService::class

        scoped { GetPlaybackItem(get(), get()) }

        scoped { ToggleMute(get()) }

        scoped { ChangeVolume(get()) }

        scoped { ChangePlaybackSpeed(get()) }

        scoped { ControlPlayback(get()) }
    }
}

private val hub = module {
    scope(HUB_SCOPE) {
        scoped { HubItemRepository() } bind ItemRepository::class

        scoped { HubReducer(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

        scoped { HubFeature(get()) } onClose { it?.close() }
    }
}

private val playlist = module {
    scope(PLAYLIST_SCOPE) {
        scoped { PlaylistItemRepository(get()) } bind ItemRepository::class

        scoped { SelectItem(get()) }

        scoped { PlaylistReducer(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

        scoped { PlaylistFeature(get()) } onClose { it?.close() }
    }
}

internal val appModule = listOf(probe, preview, player, renderer, navigation, item, playback, hub, playlist)