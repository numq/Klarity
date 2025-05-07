import com.github.numq.klarity.core.player.KlarityPlayer

abstract class JNITest {
    private val pathToBinaries = Thread.currentThread().contextClassLoader.getResource("bin")?.file

    init {
        KlarityPlayer.load(
            avutil = "$pathToBinaries\\avutil-59.dll",
            swresample = "$pathToBinaries\\swresample-5.dll",
            swscale = "$pathToBinaries\\swscale-8.dll",
            avcodec = "$pathToBinaries\\avcodec-61.dll",
            avformat = "$pathToBinaries\\avformat-61.dll",
            avfilter = "$pathToBinaries\\avfilter-10.dll",
            avdevice = "$pathToBinaries\\avdevice-61.dll",
            portaudio = "$pathToBinaries\\portaudio.dll",
            klarity = "$pathToBinaries\\klarity.dll",
        ).getOrThrow()
    }
}