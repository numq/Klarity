import com.github.numq.klarity.core.player.KlarityPlayer

abstract class JNITest {
    companion object {
        private val pathToBinaries = this::class.java.getResource("bin")?.path!!

        init {
            KlarityPlayer.load(
                avutil = "$pathToBinaries\\avutil-59.dll",
                swscale = "$pathToBinaries\\swscale-8.dll",
                swresample = "$pathToBinaries\\swresample-5.dll",
                avcodec = "$pathToBinaries\\avcodec-61.dll",
                avformat = "$pathToBinaries\\avformat-61.dll",
                portaudio = "$pathToBinaries\\libportaudio.dll",
                klarity = "$pathToBinaries\\klarity.dll",
            ).getOrThrow()
        }
    }
}
