package io.github.numq.klarity.snapshot

import io.github.numq.klarity.format.Format
import io.github.numq.klarity.frame.Frame
import java.io.Closeable

data class Snapshot(val format: Format.Video, val frame: Frame.Content.Video) : Closeable {
    override fun close() = frame.close()
}