package di

import hub.*
import hub.presentation.*
import io.github.numq.klarity.format.VideoFormat
import io.github.numq.klarity.player.KlarityPlayer
import io.github.numq.klarity.probe.ProbeManager
import io.github.numq.klarity.queue.MediaQueue
import io.github.numq.klarity.renderer.Renderer
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
import preview.PreviewService
import preview.ResetPreview
import probe.ProbeService
import renderer.RendererManager
import snapshot.SnapshotRepository

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

    scope(HUB_SCOPE) {
        factory { ResetPreview(get(), get()) }
    }

//    scope(PLAYLIST_SCOPE) {
//        scoped { StartPreview(get(), get(), get()) }
//
//        scoped { StopPreview(get()) }
//    }
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
    single { (format: VideoFormat) -> Renderer.create(format = format).getOrThrow() } onClose {
        runBlocking {
            it?.close()?.getOrThrow()
        }
    }

    scope(HUB_SCOPE) {
//        scoped { (format: VideoFormat) -> Renderer.create(format = format).getOrThrow() } onClose {
//            runBlocking {
//                it?.close()?.getOrThrow()
//            }
//        }

//        scoped { RendererRepository.Implementation() } bind RendererRepository::class onClose {
//            runBlocking {
//                it?.close()?.getOrThrow()
//            }
//        }
//
//        scoped { RendererManager.Implementation(get(), get()) } bind RendererManager::class
    }

    scope(PLAYLIST_SCOPE) {

//        scoped { RendererRepository.Implementation() } bind RendererRepository::class onClose {
//            runBlocking {
//                it?.close()?.getOrThrow()
//            }
//        }

        scoped { RendererManager.Implementation(get(), get()) } bind RendererManager::class
    }
}

private val hub = module {
    scope(HUB_SCOPE) {
        scoped { HubRepository.Implementation() } bind HubRepository::class

        scoped { GetHubItems(get()) }

        scoped { AddHubItem(get(), get(), get(), get()) }

        scoped { RemoveHubItem(get(), get()) }

        scoped { StartHubPlayback(get(), get(), get(), get()) }

        scoped { StartHubPreview(get(), get(), get()) }

        scoped { StopHubPlayback(get(), get()) }

        scoped { StopHubPreview(get(), get(), get()) }

        scoped { HubInteractionReducer() }

        scoped { HubPlaybackReducer(get(), get(), get(), get()) }

        scoped { HubPreviewReducer(get(), get()) }

        scoped { HubReducer(get(), get(), get(), get(), get(), get(), get()) }

        scoped { HubFeature(get()) } onClose { it?.close() }
    }
}

private val navigation = module {
    single { NavigationReducer() }

    single { NavigationFeature(get()) } onClose { it?.close() }
}

private val playback = module {
    scope(HUB_SCOPE) {
        scoped { PlaybackService.Implementation(get()) } bind PlaybackService::class onClose {
            runBlocking {
                it?.close()?.getOrThrow()
            }
        }

        scoped { ChangePlaybackSpeed(get()) }

        scoped { ChangeVolume(get()) }

        scoped { GetHubPlaybackState(get(), get()) }

        scoped { ToggleMute(get()) }
    }

    scope(PLAYLIST_SCOPE) {
        scoped { PlaybackService.Implementation(get()) } bind PlaybackService::class onClose {
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

        scoped { AddPlaylistItem(get(), get(), get(), get()) }

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

private val snapshot = module {
    scope(HUB_SCOPE) {
        scoped { SnapshotRepository.Implementation() } bind SnapshotRepository::class onClose {
            runBlocking {
                it?.close()?.getOrThrow()
            }
        }
    }

    scope(PLAYLIST_SCOPE) {
        scoped { SnapshotRepository.Implementation() } bind SnapshotRepository::class onClose {
            runBlocking {
                it?.close()?.getOrThrow()
            }
        }
    }
}

internal val appModule = listOf(probe, preview, player, renderer, hub, navigation, playback, playlist, snapshot)