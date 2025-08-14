package io.github.numq.example.playlist

import io.github.numq.example.preview.TimelinePreviewService
import io.github.numq.example.usecase.UseCase
import kotlin.time.Duration

class GetTimelinePreview(
    private val timelinePreviewService: TimelinePreviewService,
) : UseCase<GetTimelinePreview.Input, Unit> {
    data class Input(val timestamp: Duration)

    override suspend fun execute(input: Input) =
        timelinePreviewService.getPreview(timestamp = input.timestamp, rendererId = "preview")
}