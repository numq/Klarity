package navigation

sealed interface NavigationCommand {
    data object NavigateToHub : NavigationCommand

    data object NavigateToPlaylist : NavigationCommand
}