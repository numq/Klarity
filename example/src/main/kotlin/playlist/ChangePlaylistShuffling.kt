package playlist

import usecase.UseCase

class ChangePlaylistShuffling(
    private val playlistService: PlaylistService,
) : UseCase<ChangePlaylistShuffling.Input, Unit> {
    data class Input(val isShuffled: Boolean)

    override suspend fun execute(input: Input) = playlistService.setShuffled(isShuffled = input.isShuffled)
}