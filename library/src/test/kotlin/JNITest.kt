import io.github.numq.klarity.player.KlarityPlayer

abstract class JNITest {
    init {
        KlarityPlayer.load(klarity = javaClass.classLoader.getResource("bin/klarity.dll")!!.path).getOrThrow()
    }
}