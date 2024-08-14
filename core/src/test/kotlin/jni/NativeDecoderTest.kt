package jni

import decoder.NativeDecoder
import decoder.NativeFrame
import kotlinx.coroutines.test.runTest
import library.Klarity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL

class NativeDecoderTest {
    companion object {
        private lateinit var audioDecoder: NativeDecoder
        private lateinit var videoDecoder: NativeDecoder
        private lateinit var mediaDecoder: NativeDecoder

        private val binaries = File(ClassLoader.getSystemResources("bin").nextElement().let(URL::getFile)).listFiles()

        private val decoderBinaries = binaries?.find { file -> file.name == "decoder" }?.listFiles()

        private val files = File(ClassLoader.getSystemResources("files").nextElement().let(URL::getFile)).listFiles()

        private val audioFile = files?.find { file -> file.nameWithoutExtension == "audio_only" }?.absolutePath

        private val videoFile = files?.find { file -> file.nameWithoutExtension == "video_only" }?.absolutePath

        private val mediaFile = files?.find { file -> file.nameWithoutExtension == "audio_video" }?.absolutePath

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            checkNotNull(decoderBinaries)
            Klarity.loadDecoder(
                ffmpegPath = decoderBinaries.first { file -> file.name == "ffmpeg" }.absolutePath,
                klarityPath = decoderBinaries.first { file -> file.name == "klarity" }.absolutePath,
                jniPath = decoderBinaries.first { file -> file.name == "jni" }.absolutePath
            ).getOrThrow()
            checkNotNull(files)
        }
    }

    @BeforeEach
    fun beforeEach() {
        audioDecoder = NativeDecoder().apply { init(audioFile, true, true) }
        videoDecoder = NativeDecoder().apply { init(videoFile, true, true) }
        mediaDecoder = NativeDecoder().apply { init(mediaFile, true, true) }
    }

    @AfterEach
    fun afterEach() {
        audioDecoder.close()
        videoDecoder.close()
        mediaDecoder.close()
    }

    @Test
    fun `get format`() = runTest {
        with(audioDecoder.format) {
            assertEquals(5_000_000L, durationMicros)
            assertEquals(44100, sampleRate)
            assertEquals(2, channels)
            assertEquals(0, width)
            assertEquals(0, height)
            assertEquals(0.0, frameRate)
        }
        with(videoDecoder.format) {
            assertEquals(5_000_000L, durationMicros)
            assertEquals(0, sampleRate)
            assertEquals(0, channels)
            assertEquals(500, width)
            assertEquals(500, height)
            assertEquals(25.0, frameRate)
        }

        with(mediaDecoder.format) {
            assertEquals(5_000_000L, durationMicros)
            assertEquals(44100, sampleRate)
            assertEquals(2, channels)
            assertEquals(500, width)
            assertEquals(500, height)
            assertEquals(25.0, frameRate)
        }
    }

    @Test
    fun `get next frame`() = runTest {
        with(audioDecoder.nextFrame()) {
            assertEquals(NativeFrame.Type.AUDIO.ordinal, type)
            assertEquals(0L, timestampMicros)
            assertTrue(bytes.isNotEmpty())
        }
        with(videoDecoder.nextFrame()) {
            assertEquals(NativeFrame.Type.VIDEO.ordinal, type)
            assertEquals(0L, timestampMicros)
            assertTrue(bytes.isNotEmpty())
        }
        with(mediaDecoder.nextFrame()) {
            assertTrue(type == NativeFrame.Type.AUDIO.ordinal || type == NativeFrame.Type.VIDEO.ordinal)
            assertEquals(0L, timestampMicros)
            assertTrue(bytes.isNotEmpty())
        }
    }

    @Test
    fun `seek media`() = runTest {
        audioDecoder.seekTo((0L..audioDecoder.format.durationMicros).random())
        videoDecoder.seekTo((0L..videoDecoder.format.durationMicros).random())
        mediaDecoder.seekTo((0L..mediaDecoder.format.durationMicros).random())
    }

    @Test
    fun `reset media`() = runTest {
        audioDecoder.reset()
        videoDecoder.reset()
        mediaDecoder.reset()
    }
}