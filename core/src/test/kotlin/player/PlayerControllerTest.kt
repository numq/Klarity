package player

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class PlayerControllerTest {
    @Test
    fun `media player creation`() {
        assertDoesNotThrow {
            PlayerController.create().close()
        }
    }
}