package io.github.numq.klarity.probe

import io.github.numq.klarity.decoder.Decoder
import io.github.numq.klarity.media.Media

/**
 * Provides information about a media file.
 */
object ProbeManager {
    /**
     * Probes the specified location.
     *
     * @param location the path or URI of the media file to probe
     *
     * @return [Result] containing [Media]
     */
    fun probe(location: String): Result<Media> = Decoder.probe(
        location = location,
        findAudioStream = true,
        findVideoStream = true,
    ).recoverCatching { t ->
        throw ProbeManagerException(t)
    }
}