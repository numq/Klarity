package io.github.numq.klarity.snapshot

import io.github.numq.klarity.format.VideoFormat
import io.github.numq.klarity.frame.Frame
import java.io.Closeable

data class Snapshot(val format: VideoFormat, val frame: Frame.Content.Video) : Closeable {
    override fun close() = frame.close()
}