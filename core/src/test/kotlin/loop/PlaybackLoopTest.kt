package loop

import JNITest
import com.github.numq.klarity.core.buffer.AudioBufferFactory
import com.github.numq.klarity.core.buffer.VideoBufferFactory
import com.github.numq.klarity.core.decoder.AudioDecoderFactory
import com.github.numq.klarity.core.decoder.ProbeDecoderFactory
import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.decoder.HardwareAcceleration
import com.github.numq.klarity.core.loop.buffer.BufferLoop
import com.github.numq.klarity.core.loop.buffer.BufferLoopFactory
import com.github.numq.klarity.core.loop.playback.PlaybackLoop
import com.github.numq.klarity.core.loop.playback.PlaybackLoopFactory
import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.sampler.Sampler
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL

class PlaybackLoopTest : JNITest() {
    private val files = File(ClassLoader.getSystemResources("files").nextElement().let(URL::getFile)).listFiles()

    private val file = files?.find { file -> file.nameWithoutExtension == "audio_video" }!!

    private val location = file.absolutePath

    private lateinit var pipeline: Pipeline

    private lateinit var bufferLoop: BufferLoop

    private lateinit var playbackLoop: PlaybackLoop

    @BeforeEach
    fun beforeEach() = runBlocking {
        val media = ProbeDecoderFactory().create(
            parameters = ProbeDecoderFactory.Parameters(
                location = location,
                findAudioStream = true,
                findVideoStream = true,
                hardwareAcceleration = HardwareAcceleration.NONE
            )
        ).getOrThrow().media

        val audioDecoder = AudioDecoderFactory().create(
            parameters = AudioDecoderFactory.Parameters(location = location)
        ).getOrThrow()

        val videoDecoder = VideoDecoderFactory().create(
            parameters = VideoDecoderFactory.Parameters(
                location = location,
                hardwareAcceleration = HardwareAcceleration.NONE
            )
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

        playbackLoop = PlaybackLoopFactory().create(
            parameters = PlaybackLoopFactory.Parameters(bufferLoop = bufferLoop, pipeline = pipeline)
        ).getOrThrow()
    }

    @Test
    fun `test start and stop lifecycle`() = runTest {
        assertTrue(bufferLoop.start({}, {}, endOfMedia = {
            assertTrue(playbackLoop.start(onTimestamp = {}, endOfMedia = {
                assertTrue(bufferLoop.stop().isSuccess)
                assertTrue(playbackLoop.stop().isSuccess)
            }).isSuccess)
        }).isSuccess)
    }
}
