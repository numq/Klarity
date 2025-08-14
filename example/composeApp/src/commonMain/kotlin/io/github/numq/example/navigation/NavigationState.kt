package io.github.numq.example.navigation

sealed interface NavigationState {
    data object Splash : NavigationState

    data object Hub : NavigationState

    data object Playlist : NavigationState
}