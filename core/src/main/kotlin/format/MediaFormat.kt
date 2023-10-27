package format

object MediaFormat {
    val audio: Set<String> = setOf("MP3", "AAC", "WAV", "FLAC", "OGG", "WMA", "AIFF")

    val video: Set<String> = setOf("MP4", "AVI", "MKV", "MOV", "WMV", "FLV", "WEBM")

    val media: Set<String> = audio.plus(video)
}