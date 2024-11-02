package com.github.numq.klarity.core.loader

import com.github.numq.klarity.core.loader.Klarity.Decoder.AVCODEC
import com.github.numq.klarity.core.loader.Klarity.Decoder.AVFORMAT
import com.github.numq.klarity.core.loader.Klarity.Decoder.AVUTIL
import com.github.numq.klarity.core.loader.Klarity.Decoder.SWRESAMPLE
import com.github.numq.klarity.core.loader.Klarity.Decoder.SWSCALE

object Klarity {
    private object Decoder {
        const val AVUTIL = "avutil-57"
        const val SWSCALE = "swscale-6"
        const val SWRESAMPLE = "swresample-4"
        const val AVCODEC = "avcodec-59"
        const val AVFORMAT = "avformat-59"
        const val KLARITY_DECODER = "libklarity_decoder"
        const val JNI_DECODER = "libjni_decoder"
    }

    private object Sampler {
        const val PORTAUDIO = "libportaudio"
        const val KLARITY_SAMPLER = "libklarity_sampler"
        const val JNI_SAMPLER = "libjni_sampler"
    }

    private val extension = when {
        System.getProperty("os.name", "").contains("win", true) -> "dll"

        System.getProperty("os.name", "").contains("mac", true) -> "dylib"

        else -> "so"
    }

    var isDecoderLoaded = false
        private set

    var isSamplerLoaded = false
        private set

    fun loadDecoder(
        ffmpegPath: String,
        klarityPath: String,
        jniPath: String,
    ) = runCatching {
        arrayOf(
            AVUTIL, SWSCALE, SWRESAMPLE, AVCODEC, AVFORMAT
        ).map { bin -> "$ffmpegPath\\$bin.$extension" }.forEach(System::load)

        System.load("$klarityPath\\${Decoder.KLARITY_DECODER}.$extension")

        System.load("$jniPath\\${Decoder.JNI_DECODER}.$extension")
    }.onSuccess { isDecoderLoaded = true }

    fun loadSampler(
        portAudioPath: String,
        klarityPath: String,
        jniPath: String,
    ) = runCatching {
        System.load("$portAudioPath\\${Sampler.PORTAUDIO}.$extension")

        System.load("$klarityPath\\${Sampler.KLARITY_SAMPLER}.$extension")

        System.load("$jniPath\\${Sampler.JNI_SAMPLER}.$extension")
    }.onSuccess { isSamplerLoaded = true }
}