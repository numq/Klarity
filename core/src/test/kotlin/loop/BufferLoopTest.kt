package loop

import com.github.numq.klarity.core.buffer.AudioBufferFactory
import com.github.numq.klarity.core.buffer.VideoBufferFactory
import com.github.numq.klarity.core.decoder.AudioDecoderFactory
import com.github.numq.klarity.core.decoder.ProbeDecoderFactory
import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.loader.Klarity
import com.github.numq.klarity.core.loop.buffer.BufferLoop
import com.github.numq.klarity.core.loop.buffer.BufferLoopFactory
import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.sampler.Sampler
import com.github.numq.klarity.core.timestamp.Timestamp
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL

class BufferLoopTest {
    init {
        File(ClassLoader.getSystemResources("bin/decoder").nextElement().let(URL::getFile)).listFiles()?.run {
            Klarity.loadDecoder(
                ffmpegPath = find { file -> file.path.endsWith("ffmpeg") }!!.path,
                klarityPath = find { file -> file.path.endsWith("klarity") }!!.path,
                jniPath = find { file -> file.path.endsWith("jni") }!!.path
            ).getOrThrow()
        }
        File(ClassLoader.getSystemResources("bin/sampler").nextElement().let(URL::getFile)).listFiles()?.run {
            Klarity.loadSampler(
                portAudioPath = find { file -> file.path.endsWith("portaudio") }!!.path,
                klarityPath = find { file -> file.path.endsWith("klarity") }!!.path,
                jniPath = find { file -> file.path.endsWith("jni") }!!.path
            ).getOrThrow()
        }
    }

    private val files = File(ClassLoader.getSystemResources("files").nextElement().let(URL::getFile)).listFiles()

    private val file = files?.find { file -> file.nameWithoutExtension == "audio_video" }!!

    private val location = file.absolutePath

    private lateinit var pipeline: Pipeline

    private lateinit var bufferLoop: BufferLoop

    @BeforeEach
    fun beforeEach() = runBlocking {
        val media = ProbeDecoderFactory().create(
            parameters = ProbeDecoderFactory.Parameters(
                location = location,
                findAudioStream = true,
                findVideoStream = true
            )
        ).getOrThrow().media

        val audioDecoder = AudioDecoderFactory().create(
            parameters = AudioDecoderFactory.Parameters(location = location)
        ).getOrThrow()

        val videoDecoder = VideoDecoderFactory().create(
            parameters = VideoDecoderFactory.Parameters(location = location)
        ).getOrThrow()

        val audioBuffer = AudioBufferFactory().create(AudioBufferFactory.Parameters(capacity = 100)).getOrThrow()

        val videoBuffer = VideoBufferFactory().create(VideoBufferFactory.Parameters(capacity = 100)).getOrThrow()

        val sampler = mockk<Sampler>(relaxed = true)

        val renderer = mockk<Renderer>(relaxed = true)

        pipeline = Pipeline.AudioVideo(
            media = media,
            audioDecoder = audioDecoder,
            videoDecoder = videoDecoder,
            audioBuffer = audioBuffer,
            videoBuffer = videoBuffer,
            sampler = sampler,
            renderer = renderer,
        )

        bufferLoop = BufferLoopFactory().create(
            parameters = BufferLoopFactory.Parameters(pipeline = pipeline)
        ).getOrThrow()
    }

    @Test
    fun `test start and stop lifecycle`() = runTest {
        val onTimestamp: suspend (Timestamp) -> Unit = {}
        val onWaiting: suspend () -> Unit = {}
        val endOfMedia: suspend () -> Unit = {}

        assertTrue(bufferLoop.start(onTimestamp, onWaiting, endOfMedia).isSuccess)

        delay(1_000L)

        assertTrue(bufferLoop.isBuffering.get())

        assertTrue(bufferLoop.stop().isSuccess)

        assertFalse(bufferLoop.isBuffering.get())
    }

    @Test
    fun `test isWaiting state changes`() = runTest {
        val onTimestamp: suspend (Timestamp) -> Unit = {}
        val onWaiting: suspend () -> Unit = {}
        val endOfMedia: suspend () -> Unit = {}

        assertTrue(bufferLoop.start(onTimestamp, onWaiting, endOfMedia).isSuccess)

        assertTrue(bufferLoop.stop().isSuccess)
    }

    @Test
    fun `test timestamp flow`() = runTest {
        val onTimestamp: suspend (Timestamp) -> Unit = {}
        val onWaiting: suspend () -> Unit = {}
        val endOfMedia: suspend () -> Unit = {}

        assertTrue(bufferLoop.start(onTimestamp, onWaiting, endOfMedia).isSuccess)

        assertTrue(bufferLoop.stop().isSuccess)
    }
}