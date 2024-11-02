package decoder

import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.loader.Klarity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.net.URL

class DecoderTest {
    init {
        File(ClassLoader.getSystemResources("bin/decoder").nextElement().let(URL::getFile)).listFiles()?.run {
            Klarity.loadDecoder(
                ffmpegPath = find { file -> file.path.endsWith("ffmpeg") }!!.path,
                klarityPath = find { file -> file.path.endsWith("klarity") }!!.path,
                jniPath = find { file -> file.path.endsWith("jni") }!!.path
            ).getOrThrow()
        }
    }

    @Test
    fun `should throw an exception on empty location`() {
        assertThrows<IllegalStateException> {
            Decoder.createProbeDecoder(
                location = "",
                findAudioStream = true,
                findVideoStream = true
            ).getOrThrow()
        }
        assertThrows<IllegalStateException> {
            Decoder.createAudioDecoder(location = "").getOrThrow()
        }
        assertThrows<IllegalStateException> {
            Decoder.createVideoDecoder(location = "").getOrThrow()
        }
    }
}