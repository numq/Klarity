package navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FeaturedPlayList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import di.Scope
import hub.presentation.HubCommand
import hub.presentation.HubFeature
import hub.presentation.HubView
import kotlinx.coroutines.launch
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.core.component.getScopeId
import org.koin.core.component.getScopeName
import playlist.presentation.PlaylistCommand
import playlist.presentation.PlaylistFeature
import playlist.presentation.PlaylistView
import renderer.RendererRegistry
import splash.SplashView

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

    val hubRendererRegistry = koinInject<RendererRegistry>(scope = hubScope)

    val playlistFeature = koinInject<PlaylistFeature>(scope = playlistScope)

    val playlistRendererRegistry = koinInject<RendererRegistry>(scope = playlistScope)

    DisposableEffect(Unit) {
        onDispose {
            hubScope.close()

            playlistScope.close()
        }
    }

    val slideAnimationSpec = remember<FiniteAnimationSpec<IntOffset>> {
        tween(durationMillis = 500, easing = LinearEasing)
    }

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
                    }) {
                    HubView(feature = hubFeature, gridState = hubGridState, rendererRegistry = hubRendererRegistry)
                }
                NavigationAnimation(
                    isActive = state is NavigationState.Playlist,
                    enter = maxWidth.value,
                    exit = maxWidth.value,
                    onAnimationEnd = {
                        coroutineScope.launch {
                            playlistFeature.execute(command = PlaylistCommand.CleanUp)
                        }
                    }) {
                    PlaylistView(
                        feature = playlistFeature,
                        listState = playlistListState,
                        rendererRegistry = playlistRendererRegistry
                    )
                }
            }
            BottomNavigation(modifier = Modifier.fillMaxWidth()) {
                BottomNavigationItem(selected = state is NavigationState.Hub, onClick = {
                    coroutineScope.launch {
                        feature.execute(NavigationCommand.NavigateToHub)
                    }
                }, enabled = state !is NavigationState.Hub, icon = {
                    Icon(Icons.Default.GridView, null)
                })
                BottomNavigationItem(selected = state is NavigationState.Playlist, onClick = {
                    coroutineScope.launch {
                        feature.execute(NavigationCommand.NavigateToPlaylist)
                    }
                }, enabled = state !is NavigationState.Playlist, icon = {
                    Icon(Icons.Default.FeaturedPlayList, null)
                })
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