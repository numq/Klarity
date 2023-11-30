package player

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File
import java.net.URL
import kotlin.time.Duration.Companion.nanoseconds

class PlayerControllerTest {
    companion object {
        private val fileUrls = ClassLoader.getSystemResources("files")
            .nextElement()
            .let(URL::getFile)
            .let(::File)
            .listFiles()
            ?.filter(File::exists)
            ?.map(File::getAbsolutePath)!!

        private val mediaUrl = File(ClassLoader.getSystemResource("files/audio_video.mp4").file).absolutePath

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            require(fileUrls.isNotEmpty())
            require(File(mediaUrl).exists())
        }
    }

    private var testScope: CoroutineScope? = null

    @BeforeEach
    fun beforeEach() {
        testScope = CoroutineScope(Dispatchers.Default + Job())
    }

    @AfterEach
    fun afterEach() {
        testScope?.cancel()
        testScope = null
    }

    @Test
    fun `static creation`() = runTest {
        assertDoesNotThrow {
            PlayerController.create().use(::assertNotNull)
        }
    }

    @Test
    fun `load and unload files`() = runTest {

        val expected = arrayOf(
            PlaybackStatus.EMPTY,
            PlaybackStatus.LOADED,
            PlaybackStatus.EMPTY,
            PlaybackStatus.LOADED,
            PlaybackStatus.EMPTY,
            PlaybackStatus.LOADED,
            PlaybackStatus.EMPTY,
        )

        val actual = mutableListOf<PlaybackStatus>()

        PlayerController.create().use { controller ->
            with(controller) {

                testScope?.launch { status.collect(actual::add) }

                fileUrls.forEach { url ->
                    controller.load(url)

                    assertEquals(url, state.value.media?.url)

                    delay(100L)

                    controller.unload()

                    assertNull(state.value.media?.url)
                }
            }
        }

        assertArrayEquals(expected, actual.toTypedArray())
    }

    @Test
    fun `play stop play`() = runTest {

        val expected = arrayOf(
            PlaybackStatus.EMPTY,
            PlaybackStatus.LOADED,
            PlaybackStatus.PLAYING,
            PlaybackStatus.STOPPED,
            PlaybackStatus.PLAYING
        )

        val actual = mutableListOf<PlaybackStatus>()

        PlayerController.create().use { controller ->
            with(controller) {

                testScope?.launch { status.collect(actual::add) }

                load(mediaUrl)

                play()

                stop()

                assertEquals(0L, state.value.playbackTimestampMillis)

                play()
            }
        }

        assertArrayEquals(expected, actual.toTypedArray())
    }

    @Test
    fun `play pause play`() = runTest {

        val expected = arrayOf(
            PlaybackStatus.EMPTY,
            PlaybackStatus.LOADED,
            PlaybackStatus.PLAYING,
            PlaybackStatus.PAUSED,
            PlaybackStatus.PLAYING
        )

        val actual = mutableListOf<PlaybackStatus>()

        PlayerController.create().use { controller ->
            with(controller) {

                testScope?.launch { status.collect(actual::add) }

                load(mediaUrl)

                play()

                pause()

                delay(500L)

                play()

            }
        }

        assertArrayEquals(expected, actual.toTypedArray())
    }

    @Test
    fun `seek after play`() = runTest {

        val expected = arrayOf(
            PlaybackStatus.EMPTY,
            PlaybackStatus.LOADED,
            PlaybackStatus.PLAYING,
            PlaybackStatus.SEEKING,
            PlaybackStatus.PLAYING
        )

        val actual = mutableListOf<PlaybackStatus>()

        PlayerController.create().use { controller ->
            with(controller) {

                testScope?.launch { status.collect(actual::add) }

                load(mediaUrl)

                play()

                seekTo((0L..state.value.media!!.durationNanos.nanoseconds.inWholeMilliseconds).random())
            }
        }

        assertArrayEquals(expected, actual.toTypedArray())
    }

    @Test
    fun `seek after pause`() = runTest {

        val expected = arrayOf(
            PlaybackStatus.EMPTY,
            PlaybackStatus.LOADED,
            PlaybackStatus.PLAYING,
            PlaybackStatus.PAUSED,
            PlaybackStatus.SEEKING,
            PlaybackStatus.PAUSED
        )

        val actual = mutableListOf<PlaybackStatus>()

        PlayerController.create().use { controller ->
            with(controller) {

                testScope?.launch { status.collect(actual::add) }

                load(mediaUrl)

                play()

                pause()

                seekTo((0L..state.value.media!!.durationNanos.nanoseconds.inWholeMilliseconds).random())
            }
        }

        assertArrayEquals(expected, actual.toTypedArray())
    }

    @Test
    fun `seek after stop`() = runTest {

        val expected = arrayOf(
            PlaybackStatus.EMPTY,
            PlaybackStatus.LOADED,
            PlaybackStatus.STOPPED,
            PlaybackStatus.SEEKING,
            PlaybackStatus.PAUSED
        )

        val actual = mutableListOf<PlaybackStatus>()

        PlayerController.create().use { controller ->
            with(controller) {

                testScope?.launch { status.collect(actual::add) }

                load(mediaUrl)

                stop()

                seekTo((0L..state.value.media!!.durationNanos.nanoseconds.inWholeMilliseconds).random())
            }
        }

        assertArrayEquals(expected, actual.toTypedArray())
    }
}