package pool

import JNITest
import io.github.numq.klarity.pool.Pool
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.Data
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PoolTest : JNITest() {
    private val poolCapacity = 4
    private val bufferCapacity = 1024

    @Test
    fun `should create pool successfully`() = runTest {
        val pool = Pool.create(poolCapacity, createItem = { Data.makeUninitialized(bufferCapacity) }).getOrThrow()
        val data = pool.acquire().getOrThrow()
        assertNotNull(data)

        pool.release(data).getOrThrow()
        pool.close().getOrThrow()
    }

    @Test
    fun `should block when pool is empty and succeed later`() = runTest {
        val pool = Pool.create(poolCapacity, createItem = { Data.makeUninitialized(bufferCapacity) }).getOrThrow()

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
        val pool = Pool.create(poolCapacity, createItem = { Data.makeUninitialized(bufferCapacity) }).getOrThrow()

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
        val pool = Pool.create(poolCapacity, createItem = { Data.makeUninitialized(bufferCapacity) }).getOrThrow()
        pool.close().getOrThrow()

        assert(pool.acquire().isFailure)

        Data.makeUninitialized(bufferCapacity).use { data ->
            assert(pool.release(data).isFailure)
        }
    }

    @Test
    fun `should fail to create pool with invalid arguments`() {
        assertThrows<IllegalArgumentException> {
            Pool.create(0, createItem = { Data.makeUninitialized(bufferCapacity) }).getOrThrow()
        }
    }

    @Test
    fun `concurrent acquire and release should not crash`() = runTest {
        val pool = Pool.create(poolCapacity, createItem = { Data.makeUninitialized(bufferCapacity) }).getOrThrow()

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