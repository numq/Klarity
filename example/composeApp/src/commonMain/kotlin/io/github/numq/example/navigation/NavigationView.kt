package io.github.numq.example.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FeaturedPlayList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.numq.example.di.Scope
import io.github.numq.example.hub.presentation.HubCommand
import io.github.numq.example.hub.presentation.HubFeature
import io.github.numq.example.hub.presentation.HubView
import io.github.numq.example.playlist.presentation.PlaylistCommand
import io.github.numq.example.playlist.presentation.PlaylistFeature
import io.github.numq.example.playlist.presentation.PlaylistView
import io.github.numq.example.renderer.RendererRegistry
import io.github.numq.example.splash.SplashView
import kotlinx.coroutines.launch
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.core.component.getScopeId
import org.koin.core.component.getScopeName

@Composable
fun NavigationView(feature: NavigationFeature) {
    val coroutineScope = rememberCoroutineScope()

    val state by feature.state.collectAsState()

    val koin = getKoin()

    val hubScope = remember(koin) {
        koin.getOrCreateScope(Scope.HUB.getScopeId(), Scope.HUB.getScopeName())
    }

    val playlistScope = remember(koin) {
        koin.getOrCreateScope(Scope.PLAYLIST.getScopeId(), Scope.PLAYLIST.getScopeName())
    }

    val hubFeature = koinInject<HubFeature>(scope = hubScope)

    val playlistFeature = koinInject<PlaylistFeature>(scope = playlistScope)

    DisposableEffect(Unit) {
        onDispose {
            hubScope.close()

            playlistScope.close()
        }
    }

    val slideAnimationSpec = remember<FiniteAnimationSpec<IntOffset>> {
        tween(durationMillis = 500, easing = LinearEasing)
    }

    var isAnimationFinished by remember { mutableStateOf(false) }

    val hubGridState = rememberLazyGridState()

    val playlistListState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            BoxWithConstraints(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                NavigationAnimation(
                    isActive = state is NavigationState.Hub,
                    enter = -maxWidth.value,
                    exit = -maxWidth.value,
                    onAnimationEnd = {
                        coroutineScope.launch {
                            hubFeature.execute(command = HubCommand.CleanUp)
                        }

                        isAnimationFinished = true
                    }) {
                    HubView(
                        feature = hubFeature,
                        gridState = hubGridState,
                        rendererRegistry = koinInject<RendererRegistry>(scope = hubScope)
                    )
                }
                NavigationAnimation(
                    isActive = state is NavigationState.Playlist,
                    enter = maxWidth.value,
                    exit = maxWidth.value,
                    onAnimationEnd = {
                        coroutineScope.launch {
                            playlistFeature.execute(command = PlaylistCommand.CleanUp)
                        }

                        isAnimationFinished = true
                    }) {
                    PlaylistView(
                        feature = playlistFeature,
                        listState = playlistListState,
                        isAnimationFinished = isAnimationFinished,
                        rendererRegistry = koinInject<RendererRegistry>(scope = playlistScope)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                with(Modifier.weight(1f).height(80.dp).background(MaterialTheme.colorScheme.surface)) {
                    Column(
                        modifier = clickable(enabled = state !is NavigationState.Hub) {
                            isAnimationFinished = false

                            coroutineScope.launch {
                                feature.execute(NavigationCommand.NavigateToHub)
                            }
                        }.alpha(if (state is NavigationState.Hub) .5f else 1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.GridView, null, tint = MaterialTheme.colorScheme.onSurface)
                        Text("Hub", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Column(
                        modifier = clickable(enabled = state !is NavigationState.Playlist) {
                            isAnimationFinished = false

                            coroutineScope.launch {
                                feature.execute(NavigationCommand.NavigateToPlaylist)
                            }
                        }.alpha(if (state is NavigationState.Playlist) .5f else 1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.FeaturedPlayList, null, tint = MaterialTheme.colorScheme.onSurface
                        )
                        Text("Playlist", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = state is NavigationState.Splash,
            enter = slideInHorizontally(animationSpec = slideAnimationSpec) { -it },
            exit = slideOutHorizontally(animationSpec = slideAnimationSpec) { it },
            modifier = Modifier.fillMaxSize()
        ) {
            SplashView {
                coroutineScope.launch {
                    feature.execute(NavigationCommand.NavigateToHub)
                }
            }
        }
    }
}