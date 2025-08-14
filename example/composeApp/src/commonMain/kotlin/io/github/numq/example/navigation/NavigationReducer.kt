package io.github.numq.example.navigation

import io.github.numq.example.feature.Reducer

class NavigationReducer : Reducer<NavigationCommand, NavigationState, NavigationEvent> {
    override suspend fun reduce(state: NavigationState, command: NavigationCommand) = when (command) {
        is NavigationCommand.NavigateToHub -> transition(NavigationState.Hub)

        is NavigationCommand.NavigateToPlaylist -> transition(NavigationState.Playlist)
    }
}