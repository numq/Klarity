package controller

import io.github.numq.klarity.buffer.Buffer
import io.github.numq.klarity.buffer.BufferFactory
import io.github.numq.klarity.command.Command
import io.github.numq.klarity.controller.DefaultPlayerController
import io.github.numq.klarity.decoder.*
import io.github.numq.klarity.format.Format
import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.hwaccel.HardwareAcceleration
import io.github.numq.klarity.loop.buffer.BufferLoop
import io.github.numq.klarity.loop.buffer.BufferLoopFactory
import io.github.numq.klarity.loop.playback.PlaybackLoop
import io.github.numq.klarity.loop.playback.PlaybackLoopFactory
import io.github.numq.klarity.media.Media
import io.github.numq.klarity.pipeline.Pipeline
import io.github.numq.klarity.pool.Pool
import io.github.numq.klarity.pool.PoolFactory
import io.github.numq.klarity.sampler.Sampler
import io.github.numq.klarity.sampler.SamplerFactory
import io.github.numq.klarity.state.Destination
import io.github.numq.klarity.state.InternalPlayerState
import io.github.numq.klarity.state.PlayerState
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Data
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class PlayerControllerStateTest {
    private lateinit var data: Data
    private lateinit var frame: Frame

    private lateinit var audioDecoder: AudioDecoder
    private lateinit var videoDecoder: VideoDecoder
    private lateinit var audioDecoderFactory: AudioDecoderFactory
    private lateinit var videoDecoderFactory: VideoDecoderFactory

    private lateinit var videoPool: Pool<Data>
    private lateinit var buffer: Buffer<Frame>
    private lateinit var poolFactory: PoolFactory
    private lateinit var bufferFactory: BufferFactory

    private lateinit var bufferLoopFactory: BufferLoopFactory
    private lateinit var playbackLoopFactory: PlaybackLoopFactory
    private lateinit var bufferLoop: BufferLoop
    private lateinit var playbackLoop: PlaybackLoop

    private lateinit var sampler: Sampler
    private lateinit var samplerFactory: SamplerFactory

    private val location = "location"

    private val mediaId = 0L

    private val mediaDuration = 10L.minutes

    private val audioFormat = Format.Audio(sampleRate = 44100, channels = 2)

    private val videoFormat = Format.Video(
        width = 100,
        height = 100,
        frameRate = 30.0,
        hardwareAcceleration = HardwareAcceleration.None,
        bufferCapacity = 1
    )

    private lateinit var media: Media

    private lateinit var pipeline: Pipeline

    private lateinit var playerController: DefaultPlayerController

    @BeforeEach
    fun beforeEach() {
        mockkObject(Decoder)
        every {
            Decoder.probe(
                location = location, findAudioStream = true, findVideoStream = false
            )
        } returns Result.success(
            Media(
                id = mediaId,
                location = location,
                duration = mediaDuration,
                audioFormat = audioFormat,
                videoFormat = null
            )
        )
        every {
            Decoder.probe(
                location = location, findAudioStream = false, findVideoStream = true
            )
        } returns Result.success(
            Media(
                id = mediaId,
                location = location,
                duration = mediaDuration,
                audioFormat = null,
                videoFormat = videoFormat
            )
        )
        every {
            Decoder.probe(
                location = location, findAudioStream = true, findVideoStream = true
            )
        } returns Result.success(
            Media(
                id = mediaId,
                location = location,
                duration = mediaDuration,
                audioFormat = audioFormat,
                videoFormat = videoFormat
            )
        )

        data = mockk(relaxed = true)

        frame = mockk(relaxed = true)

        audioDecoder = mockk(relaxed = true, relaxUnitFun = true)
        coEvery { audioDecoder.decodeAudio() } returns Result.success(frame)
        coEvery { audioDecoder.seekTo(timestamp = any(), keyFramesOnly = any()) } returns Result.success(Unit)
        coEvery { audioDecoder.close() } returns Result.success(Unit)

        videoDecoder = mockk(relaxed = true, relaxUnitFun = true)
        coEvery { videoDecoder.decodeVideo(data = any()) } returns Result.success(frame)
        coEvery { videoDecoder.seekTo(timestamp = any(), keyFramesOnly = any()) } returns Result.success(Unit)
        coEvery { videoDecoder.close() } returns Result.success(Unit)

        audioDecoderFactory = mockk(relaxed = true)
        every { audioDecoderFactory.create(any()) } returns Result.success(audioDecoder)

        videoDecoderFactory = mockk(relaxed = true)
        every { videoDecoderFactory.create(any()) } returns Result.success(videoDecoder)

        videoPool = mockk(relaxed = true)
        coEvery { videoPool.acquire() } returns Result.success(data)
        coEvery { videoPool.close() } returns Result.success(Unit)

        buffer = mockk(relaxed = true)
        coEvery { buffer.close() } returns Result.success(Unit)

        poolFactory = mockk(relaxed = true)
        every { poolFactory.create(any()) } returns Result.success(videoPool)

        bufferFactory = mockk(relaxed = true)
        every { bufferFactory.create(any()) } returns Result.success(buffer)

        bufferLoop = mockk(relaxed = true)
        coEvery { bufferLoop.close() } returns Result.success(Unit)

        playbackLoop = mockk(relaxed = true)
        coEvery { playbackLoop.close() } returns Result.success(Unit)

        bufferLoopFactory = mockk(relaxed = true)
        every { bufferLoopFactory.create(any()) } returns Result.success(bufferLoop)

        playbackLoopFactory = mockk(relaxed = true)
        every { playbackLoopFactory.create(any()) } returns Result.success(playbackLoop)

        sampler = mockk(relaxed = true)
        coEvery { sampler.close() } returns Result.success(Unit)

        samplerFactory = mockk()
        every { samplerFactory.create(any()) } returns Result.success(sampler)

        playerController = DefaultPlayerController(
            initialSettings = null,
            audioDecoderFactory = audioDecoderFactory,
            videoDecoderFactory = videoDecoderFactory,
            poolFactory = poolFactory,
            bufferFactory = bufferFactory,
            bufferLoopFactory = bufferLoopFactory,
            playbackLoopFactory = playbackLoopFactory,
            samplerFactory = samplerFactory
        )

        media = Media(
            id = mediaId,
            location = location,
            duration = mediaDuration,
            audioFormat = audioFormat,
            videoFormat = videoFormat
        )

        pipeline = Pipeline(
            media = media, audioPipeline = Pipeline.AudioPipeline(
                decoder = audioDecoder,
                buffer = buffer,
                sampler = sampler,
            ), videoPipeline = Pipeline.VideoPipeline(
                decoder = videoDecoder, pool = videoPool, buffer = buffer
            )
        )
    }

    @AfterEach
    fun afterEach() {
        clearAllMocks()
    }

    @Test
    fun `state emission`() = runTest {
        var previousState: InternalPlayerState.Ready?

        val expectedInternalStates = buildList {
            add(InternalPlayerState.Preparing)
            add(
                InternalPlayerState.Ready.Stopped(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = null
                ).also { previousState = it })
            add(
                InternalPlayerState.Ready.Transition(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = previousState!!,
                    destination = Destination.PLAYING
                )
            )
            add(
                InternalPlayerState.Ready.Playing(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = previousState
                ).also { previousState = it })
            add(
                InternalPlayerState.Ready.Transition(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = previousState!!,
                    destination = Destination.PAUSED
                )
            )
            add(
                InternalPlayerState.Ready.Paused(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = previousState
                ).also { previousState = it })
            add(
                InternalPlayerState.Ready.Transition(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = previousState!!,
                    destination = Destination.PLAYING
                )
            )
            add(
                InternalPlayerState.Ready.Playing(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = previousState
                ).also { previousState = it })
            add(
                InternalPlayerState.Ready.Transition(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = previousState!!,
                    destination = Destination.SEEKING
                )
            )
            add(
                InternalPlayerState.Ready.Seeking(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = previousState
                ).also { previousState = it })
            add(
                InternalPlayerState.Ready.Transition(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = previousState!!,
                    destination = Destination.PAUSED
                )
            )
            add(
                InternalPlayerState.Ready.Paused(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = previousState
                ).also { previousState = it })
            add(
                InternalPlayerState.Ready.Transition(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = previousState!!,
                    destination = Destination.STOPPED
                )
            )
            add(
                InternalPlayerState.Ready.Stopped(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = previousState
                ).also { previousState = it })
            add(InternalPlayerState.Releasing(previousState = previousState!!))
            add(InternalPlayerState.Empty)
        }

        val expectedStates = listOf(
            PlayerState.Ready.Stopped(media = media),
            PlayerState.Ready.Playing(media = media),
            PlayerState.Ready.Paused(media = media),
            PlayerState.Ready.Playing(media = media),
            PlayerState.Ready.Paused(media = media),
            PlayerState.Ready.Stopped(media = media),
            PlayerState.Empty
        )

        val internalStates = mutableListOf<InternalPlayerState>()

        val states = mutableListOf<PlayerState>()

        playerController.onInternalPlayerState = { internalState ->
            internalStates.add(internalState)
        }

        listOf(
            Command.Prepare(location, 1, 1, null),
            Command.Play,
            Command.Pause,
            Command.Resume,
            Command.SeekTo(Duration.ZERO),
            Command.Stop,
            Command.Release
        ).map { command ->
            playerController.execute(command = command).getOrThrow()

            states.add(playerController.state.value)

            delay(1_000L)
        }

        expectedInternalStates.forEachIndexed { index, expectedState ->
            assertEquals(expectedState, internalStates[index])
        }

        expectedStates.forEachIndexed { index, expectedState ->
            assertEquals(expectedState, states[index])
        }
    }
}