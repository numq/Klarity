package playlist

import usecase.UseCase

class NextPlaylistItem(
    private val playlistService: PlaylistService,
) : UseCase<Unit, Unit> {
    override suspend fun execute(input: Unit) = playlistService.next()
}