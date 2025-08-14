package io.github.numq.example.navigation

sealed interface NavigationCommand {
    data object NavigateToHub : NavigationCommand

    data object NavigateToPlaylist : NavigationCommand
}