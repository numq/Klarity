package navigation

import feature.Reducer

class NavigationReducer : Reducer<NavigationCommand, NavigationState, NavigationEvent> {
    override suspend fun reduce(state: NavigationState, command: NavigationCommand) = when (command) {
        is NavigationCommand.NavigateToHub -> transition(NavigationState.Hub)

        is NavigationCommand.NavigateToPlaylist -> transition(NavigationState.Playlist)
    }
}