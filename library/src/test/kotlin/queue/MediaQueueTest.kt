package queue

import io.github.numq.klarity.queue.MediaQueue
import io.github.numq.klarity.queue.MediaQueueSelection
import io.github.numq.klarity.queue.RepeatMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MediaQueueTest {
    private lateinit var mediaQueue: MediaQueue<Int, Int>

    @BeforeEach
    fun beforeEach() {
        mediaQueue = MediaQueue.create<Int, Int>().getOrThrow()
    }

    @Test
    fun `add item`() = runTest {
        mediaQueue.add(1)

        assertEquals(listOf(1), mediaQueue.items.first())
    }

    @Test
    fun `delete item`() = runTest {
        mediaQueue.add(1)
        mediaQueue.add(2)

        mediaQueue.delete(1)

        assertEquals(listOf(2), mediaQueue.items.first())
    }

    @Test
    fun `select and delete selected item`() = runTest {
        mediaQueue.add(1)
        mediaQueue.add(2)
        mediaQueue.select(1)

        mediaQueue.delete(1)

        assertEquals(2, (mediaQueue.selection.first() as MediaQueueSelection.Present).item)

        mediaQueue.delete(2)

        assertTrue(mediaQueue.selection.first() is MediaQueueSelection.Absent)
    }

    @Test
    fun `shuffle functionality`() = runTest {
        mediaQueue.add(1)
        mediaQueue.add(2)
        mediaQueue.add(3)

        mediaQueue.setShuffleEnabled(true)

        assertTrue(mediaQueue.isShuffled.first())

        val shuffledItems = mediaQueue.items.first()
        assertTrue(shuffledItems.containsAll(listOf(1, 2, 3)))
    }

    @Test
    fun `repeat mode NONE`() = runTest {
        mediaQueue.add(1)
        mediaQueue.add(2)
        mediaQueue.setRepeatMode(RepeatMode.NONE)

        mediaQueue.select(1)
        assertFalse(mediaQueue.hasPrevious.first())
        assertTrue(mediaQueue.hasNext.first())
    }

    @Test
    fun `repeat mode CIRCULAR`() = runTest {
        mediaQueue.add(1)
        mediaQueue.add(2)
        mediaQueue.setRepeatMode(RepeatMode.CIRCULAR)

        mediaQueue.select(1)
        assertTrue(mediaQueue.hasPrevious.first())
        assertTrue(mediaQueue.hasNext.first())
    }

    @Test
    fun `replace item`() = runTest {
        mediaQueue.add(1)
        mediaQueue.add(2)

        mediaQueue.replace(1, 10)

        assertEquals(listOf(10, 2), mediaQueue.items.first())
    }

    @Test
    fun `previous item`() = runTest {
        mediaQueue.add(1)
        mediaQueue.add(2)
        mediaQueue.select(2)

        mediaQueue.previous()

        val selectedItem = mediaQueue.selection.first()
        assertTrue(selectedItem is MediaQueueSelection.Present)
        assertEquals(1, (selectedItem as MediaQueueSelection.Present).item)
    }

    @Test
    fun `next item`() = runTest {
        mediaQueue.add(1)
        mediaQueue.add(2)
        mediaQueue.select(1)

        mediaQueue.next()

        val selectedItem = mediaQueue.selection.first()
        assertTrue(selectedItem is MediaQueueSelection.Present)
        assertEquals(2, (selectedItem as MediaQueueSelection.Present).item)
    }

    @Test
    fun `clear queue`() = runTest {
        mediaQueue.add(1)
        mediaQueue.add(2)
        mediaQueue.select(1)

        mediaQueue.clear()

        val selectedItem = mediaQueue.selection.first()
        assertTrue(selectedItem is MediaQueueSelection.Absent)
        assertEquals(emptyList<Int>(), mediaQueue.items.first())
    }
}