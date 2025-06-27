package playlist.presentation

import feature.Reducer
import feature.Transition
import playlist.GetTimelinePreview

class PlaylistPreviewReducer(
    private val getTimelinePreview: GetTimelinePreview,
) : Reducer<PlaylistCommand.Preview, PlaylistState, PlaylistEvent> {
    override suspend fun reduce(
        state: PlaylistState, command: PlaylistCommand.Preview,
    ): Transition<PlaylistState, PlaylistEvent> = when (command) {
        is PlaylistCommand.Preview.GetTimestamp -> runCatching {
            val timestamp = command.previewTimestamp?.timestamp

            if (timestamp != null) {
                getTimelinePreview.execute(input = GetTimelinePreview.Input(timestamp = timestamp))
            }
        }.fold(onSuccess = {
            transition(state.copy(previewTimestamp = command.previewTimestamp))
        }, onFailure = {
            transition(state, PlaylistEvent.Error("Could not get preview timestamp: ${it.message}"))
        })
    }
}