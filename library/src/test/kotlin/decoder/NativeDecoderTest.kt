package decoder

import JNITest
import io.github.numq.klarity.decoder.NativeDecoder
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Data
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.net.URL

class NativeDecoderTest : JNITest() {
    private val files = File(ClassLoader.getSystemResources("files").nextElement().let(URL::getFile)).listFiles()

    private val audioFile = files?.find { file -> file.nameWithoutExtension == "audio_only" }?.absolutePath!!

    private val videoFile = files?.find { file -> file.nameWithoutExtension == "video_only" }?.absolutePath!!

    private val mediaFile = files?.find { file -> file.nameWithoutExtension == "audio_video" }?.absolutePath!!

    @Test
    fun `should create and close decoder`() = runTest {
        arrayOf(audioFile, videoFile, mediaFile).forEach { location ->
            val decoder = NativeDecoder(
                location = location,
                findAudioStream = true,
                findVideoStream = true,
                decodeAudioStream = true,
                decodeVideoStream = true
            )

            assert(decoder.getNativeHandle() != -1L)

            assert(decoder.format.isSuccess)

            decoder.close()
            assertThrows<IllegalStateException> {
                decoder.getNativeHandle()
            }
        }
    }

    @Test
    fun `should decode video and audio safely`() = runTest {
        val bufferSize = 1024
        val nativeBuffer = Data.makeUninitialized(bufferSize)

        val decoder = NativeDecoder(
            location = mediaFile,
            findAudioStream = true,
            findVideoStream = true,
            decodeAudioStream = true,
            decodeVideoStream = true
        )

        val audioFrame = decoder.decodeAudio()
        val videoFrame = decoder.decodeVideo(nativeBuffer.writableData(), bufferSize)

        assertTrue(audioFrame.isSuccess || videoFrame.isSuccess)

        nativeBuffer.close()
        decoder.close()
    }

    @Test
    fun `should seek and reset without failure`() = runTest {
        val decoder = NativeDecoder(
            location = mediaFile,
            findAudioStream = true,
            findVideoStream = true,
            decodeAudioStream = true,
            decodeVideoStream = true
        )

        val seekResult = decoder.seekTo(1_000_000, keyframesOnly = true)
        assertTrue(seekResult.isSuccess)
        assertTrue(seekResult.getOrThrow() >= 0)

        val resetResult = decoder.reset()
        assertTrue(resetResult.isSuccess)

        decoder.close()
    }

    @Test
    fun `should return available hardware accelerations`() {
        val hardware = NativeDecoder.getAvailableHardwareAcceleration()
        assertNotNull(hardware)
    }
}