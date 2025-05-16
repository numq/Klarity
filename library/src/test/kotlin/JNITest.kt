import io.github.numq.klarity.player.KlarityPlayer

abstract class JNITest {
    init {
        KlarityPlayer.load().getOrThrow()
    }
}