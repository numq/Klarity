package navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FeaturedPlayList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.FeaturedPlayList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.onExternalDrag
import androidx.compose.ui.unit.IntOffset
import hub.HubScreen
import kotlinx.coroutines.channels.Channel
import notification.Notification
import notification.NotificationComponent
import playlist.PlaylistScreen
import splash.SplashScreen
import java.io.File
import java.net.URI

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Navigation(openFileChooser: () -> List<File>) {
    val notifications = remember { Channel<Notification>(Channel.BUFFERED) }

    DisposableEffect(Unit) {
        onDispose {
            notifications.close()
        }
    }

    val (transition, setTransition) = remember {
        mutableStateOf(
            Transition(
                previous = Destination.SPLASH,
                current = Destination.SPLASH
            )
        )
    }

    val slideAnimationSpec = remember<FiniteAnimationSpec<IntOffset>> {
        tween(durationMillis = 500, easing = LinearEasing)
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = transition.current != Destination.SPLASH,
            enter = slideInHorizontally(animationSpec = slideAnimationSpec) { -it },
            exit = slideOutHorizontally(animationSpec = slideAnimationSpec) { it }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    this@Column.AnimatedVisibility(
                        visible = transition.current == Destination.HUB,
                        enter = slideInHorizontally(animationSpec = slideAnimationSpec) {
                            if (transition.previous.ordinal > transition.current.ordinal) -it else it
                        },
                        exit = slideOutHorizontally(animationSpec = slideAnimationSpec) {
                            if (transition.previous.ordinal > transition.current.ordinal) it else -it
                        }
                    ) {
                        HubScreen(
                            openFileChooser = {
                                openFileChooser().filter(File::exists)
                            },
                            upload = { locations ->
                                locations.map(::URI).map(URI::getPath).map(::File).filter(File::exists)
                            },
                            notify = notifications::trySend
                        )
                    }
                    this@Column.AnimatedVisibility(
                        visible = transition.current == Destination.PLAYLIST,
                        enter = slideInHorizontally(animationSpec = slideAnimationSpec) {
                            if (transition.previous.ordinal > transition.current.ordinal) -it else it
                        },
                        exit = slideOutHorizontally(animationSpec = slideAnimationSpec) {
                            if (transition.previous.ordinal > transition.current.ordinal) it else -it
                        }
                    ) {
                        PlaylistScreen(
                            openFileChooser = {
                                openFileChooser().filter(File::exists)
                            },
                            upload = { locations ->
                                locations.map(::URI).map(URI::getPath).map(::File).filter(File::exists)
                            },
                            notify = notifications::trySend
                        )
                    }
                }
                BottomNavigation {
                    BottomNavigationItem(selected = transition.current == Destination.HUB, onClick = {
                        setTransition(Transition(previous = transition.current, current = Destination.HUB))
                    }, enabled = transition.current != Destination.HUB, icon = {
                        Icon(
                            if (transition.current == Destination.HUB) Icons.Default.GridView else Icons.Outlined.GridView,
                            null
                        )
                    }, label = {
                        Text("Hub")
                    }, modifier = Modifier.onExternalDrag(onDragStart = {
                        setTransition(Transition(previous = transition.current, current = Destination.HUB))
                    }))
                    BottomNavigationItem(selected = transition.current == Destination.PLAYLIST, onClick = {
                        setTransition(Transition(previous = transition.current, current = Destination.PLAYLIST))
                    }, enabled = transition.current != Destination.PLAYLIST, icon = {
                        Icon(
                            if (transition.current == Destination.PLAYLIST) Icons.Default.FeaturedPlayList else Icons.Outlined.FeaturedPlayList,
                            null
                        )
                    }, label = {
                        Text("Playlist")
                    }, modifier = Modifier.onExternalDrag(onDragStart = {
                        setTransition(Transition(previous = transition.current, current = Destination.PLAYLIST))
                    }))
                }
            }
        }
        AnimatedVisibility(
            visible = transition.current == Destination.SPLASH,
            enter = slideInHorizontally(animationSpec = slideAnimationSpec) { -it },
            exit = slideOutHorizontally(animationSpec = slideAnimationSpec) { it },
            modifier = Modifier.fillMaxSize()
        ) {
            SplashScreen {
                setTransition(Transition(previous = Destination.SPLASH, current = Destination.HUB))
            }
        }
        NotificationComponent(notifications = notifications)
    }
}