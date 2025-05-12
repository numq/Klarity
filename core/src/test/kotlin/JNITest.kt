import com.github.numq.klarity.core.player.KlarityPlayer

abstract class JNITest {
    init {
        KlarityPlayer.load().getOrThrow()
    }
}