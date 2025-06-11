package di

import hub.HubFeature
import hub.HubReducer
import io.github.numq.klarity.player.KlarityPlayer
import io.github.numq.klarity.probe.ProbeManager
import item.AddItem
import item.GetItems
import item.ItemRepository
import item.RemoveItem
import kotlinx.coroutines.runBlocking
import navigation.NavigationFeature
import navigation.NavigationReducer
import org.koin.core.component.getScopeName
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose
import playback.ChangePlaybackSpeed
import playback.ControlPlayback
import playback.GetPlaybackState
import playback.PlaybackService
import playlist.PlaylistFeature
import playlist.PlaylistReducer
import preview.*
import probe.ProbeService
import renderer.RendererRepository
import renderer.RendererService

private val HUB_SCOPE = Scope.HUB.getScopeName()

private val PLAYLIST_SCOPE = Scope.PLAYLIST.getScopeName()

private val navigation = module {
    single { NavigationReducer() }

    single { NavigationFeature(get()) } onClose { it?.close() }
}

private val item = module {
    scope(HUB_SCOPE) {
        scoped { ItemRepository.Implementation() } bind ItemRepository::class

        scoped { GetItems(get()) }

        scoped { AddItem(get(), get(), get(), get(), get(), get()) }

        scoped { RemoveItem(get(), get(), get()) }
    }

    scope(PLAYLIST_SCOPE) {
        scoped { ItemRepository.Implementation() } bind ItemRepository::class

        scoped { GetItems(get()) }

        scoped { AddItem(get(), get(), get(), get(), get(), get()) }

        scoped { RemoveItem(get(), get(), get()) }
    }
}

private val probe = module {
    single { ProbeManager }

    single { ProbeService(get()) }
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

private val renderer = module {
    scope(HUB_SCOPE) {
        scoped { RendererRepository.Implementation() } bind RendererRepository::class onClose {
            runBlocking {
                it?.close()?.getOrThrow()
            }
        }

        scoped { RendererService.Implementation(get()) } bind RendererService::class
    }

    scope(PLAYLIST_SCOPE) {
        scoped { RendererRepository.Implementation() } bind RendererRepository::class onClose {
            runBlocking {
                it?.close()?.getOrThrow()
            }
        }

        scoped { RendererService.Implementation(get()) } bind RendererService::class
    }
}

private val playback = module {
    scope(HUB_SCOPE) {
        scoped { KlarityPlayer.create().getOrThrow() }

        scoped { PlaybackService(get()) }

        scoped { GetPlaybackState(get()) }

        scoped { ChangePlaybackSpeed(get()) }

        scoped { ControlPlayback(get()) }
    }

    scope(PLAYLIST_SCOPE) {
        scoped { KlarityPlayer.create().getOrThrow() }

        scoped { PlaybackService(get()) }

        scoped { GetPlaybackState(get()) }

        scoped { ChangePlaybackSpeed(get()) }

        scoped { ControlPlayback(get()) }
    }
}

private val hub = module {
    scope(HUB_SCOPE) {
        scoped { HubReducer(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

        scoped { HubFeature(get()) } onClose { it?.close() }
    }
}

private val playlist = module {
    scope(PLAYLIST_SCOPE) {
        scoped { PlaylistReducer(get(), get(), get(), get(), get(), get(), get(), get()) }

        scoped { PlaylistFeature(get()) } onClose { it?.close() }
    }
}

internal val appModule = listOf(navigation, item, probe, renderer, playback, preview, hub, playlist)