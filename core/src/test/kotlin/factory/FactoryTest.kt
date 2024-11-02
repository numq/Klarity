package factory

import com.github.numq.klarity.core.factory.Factory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FactoryTest {
    private val factory: Factory<Int, String> = StringFactory()

    @Test
    fun `create returns success for positive numbers`() {
        val result = factory.create(5)
        assertTrue(result.isSuccess)
        assertEquals("Positive number: 5", result.getOrNull())
    }

    @Test
    fun `create returns failure for zero`() {
        val result = factory.create(0)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Negative number or zero: 0", result.exceptionOrNull()?.message)
    }

    @Test
    fun `create returns failure for negative numbers`() {
        val result = factory.create(-1)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Negative number or zero: -1", result.exceptionOrNull()?.message)
    }
}