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
import playback.ChangePlaybackSpeed
import playback.ChangeVolume
import playback.PlaybackService
import playback.ToggleMute
import playlist.*
import playlist.presentation.*
import preview.LoopPreviewService
import preview.TimelinePreviewService
import renderer.RendererRegistry
import renderer.RendererService

private val HUB_SCOPE = Scope.HUB.getScopeName()

private val PLAYLIST_SCOPE = Scope.PLAYLIST.getScopeName()

private val application = module {
    single { ProbeManager }

    single { SnapshotManager }

    scope(HUB_SCOPE) {
        scoped { KlarityPlayer.create().getOrThrow() } onClose {
            runBlocking {
                it?.close()?.getOrThrow()
            }
        }
    }

    scope(PLAYLIST_SCOPE) {
        scoped { KlarityPlayer.create().getOrThrow() } onClose {
            runBlocking {
                it?.close()?.getOrThrow()
            }
        }

        scoped { MediaQueue.create<Item>().getOrThrow() }
    }
}

private val preview = module {
    scope(HUB_SCOPE) {
        scoped { LoopPreviewService.Implementation(get()) } bind LoopPreviewService::class onClose {
            runBlocking {
                it?.close()?.getOrThrow()
            }
        }
    }

    scope(PLAYLIST_SCOPE) {
        scoped { TimelinePreviewService.Implementation(get()) } bind TimelinePreviewService::class onClose {
            runBlocking {
                it?.close()?.getOrThrow()
            }
        }
    }
}


private val renderer = module {
    single { RendererRegistry.Implementation(get()) } bind RendererRegistry::class onClose {
        runBlocking {
            it?.close()?.getOrThrow()
        }
    }

    single { RendererService.Implementation(get()) } bind RendererService::class
}

private val hub = module {
    scope(HUB_SCOPE) {
        scoped { HubRepository.Implementation(get()) } bind HubRepository::class onClose {
            runBlocking {
                it?.close()?.getOrThrow()
            }
        }

        scoped { GetHub(get(), get()) }

        scoped { AddHubItem(get(), get(), get()) }

        scoped { RemoveHubItem(get()) }

        scoped { StartHubPlayback(get(), get(), get(), get()) }

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
    Scope.entries.forEach { scope ->
        scope(scope.getScopeName()) {
            scoped { PlaybackService.Implementation(get(), get(), get()) } bind PlaybackService::class onClose {
                runBlocking {
                    it?.close()?.getOrThrow()
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

        scoped { PlaylistRepository.Implementation(get(), get()) } bind PlaylistRepository::class onClose {
            runBlocking {
                it?.close()?.getOrThrow()
            }
        }

        scoped { GetPlaylist(get()) }

        scoped { AddPlaylistItem(get(), get(), get()) }

        scoped { RemovePlaylistItem(get(), get(), get()) }

        scoped { SelectPlaylistItem(get()) }

        scoped { ChangePlaylistMode(get()) }

        scoped { ChangePlaylistShuffling(get()) }

        scoped { PreviousPlaylistItem(get()) }

        scoped { NextPlaylistItem(get()) }

        scoped { ControlPlaylistPlayback(get()) }

        scoped { GetTimelinePreview(get()) }

        scoped { PlaylistInteractionReducer() }

        scoped { PlaylistPlaybackReducer(get(), get(), get(), get(), get()) }

        scoped { PlaylistPreviewReducer(get()) }

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

internal val appModule = listOf(application, preview, renderer, hub, navigation, playback, playlist)