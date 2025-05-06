package pool

import JNITest
import com.github.numq.klarity.core.data.Data
import com.github.numq.klarity.core.pool.Pool
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PoolTest : JNITest() {
    private val poolCapacity = 4
    private val bufferCapacity = 1024

    @Test
    fun `should create pool successfully`() = runTest {
        val pool = Pool.create(poolCapacity, bufferCapacity).getOrThrow()
        val data = pool.acquire().getOrThrow()
        assertNotNull(data)

        pool.release(data).getOrThrow()
        pool.close().getOrThrow()
    }

    @Test
    fun `should block when pool is empty and succeed later`() = runTest {
        val pool = Pool.create(poolCapacity, bufferCapacity).getOrThrow()

        val acquired = mutableListOf<Data>()
        repeat(poolCapacity) {
            acquired += pool.acquire().getOrThrow()
        }

        val deferred = async {
            withTimeout(500) {
                pool.acquire().getOrThrow()
            }
        }

        delay(100)

        pool.release(acquired.first())

        val newItem = deferred.await()
        assertNotNull(newItem)

        acquired.drop(1).forEach { pool.release(it) }
        pool.release(newItem).getOrThrow()
        pool.close().getOrThrow()
    }

    @Test
    fun `should handle reset by reinitializing pool`() = runTest {
        val pool = Pool.create(poolCapacity, bufferCapacity).getOrThrow()

        val beforeReset = mutableSetOf<Data>()
        repeat(poolCapacity) {
            beforeReset += pool.acquire().getOrThrow()
        }

        beforeReset.forEach { it.close() }

        assert(pool.reset().isSuccess)

        repeat(poolCapacity) {
            val item = pool.acquire().getOrThrow()
            assertNotNull(item)
            pool.release(item).getOrThrow()
        }

        pool.close()
    }

    @Test
    fun `should not allow acquire or release after close`() = runTest {
        val pool = Pool.create(poolCapacity, bufferCapacity).getOrThrow()
        pool.close().getOrThrow()

        Data.allocate(bufferCapacity).use { data ->
            assert(pool.acquire().isFailure)

            assert(pool.release(data).isFailure)
        }
    }

    @Test
    fun `should fail to create pool with invalid arguments`() {
        assertThrows<IllegalArgumentException> {
            Pool.create(0, bufferCapacity).getOrThrow()
        }

        assertThrows<IllegalArgumentException> {
            Pool.create(poolCapacity, 0).getOrThrow()
        }
    }

    @Test
    fun `concurrent acquire and release should not crash`() = runTest {
        val pool = Pool.create(poolCapacity, bufferCapacity).getOrThrow()

        val jobs = List(10) {
            launch(Dispatchers.Default) {
                repeat(100) {
                    val item = pool.acquire().getOrThrow()
                    delay(1)
                    pool.release(item).getOrThrow()
                }
            }
        }

        jobs.joinAll()
        pool.close().getOrThrow()
    }
}