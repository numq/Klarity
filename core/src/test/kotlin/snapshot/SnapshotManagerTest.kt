package snapshot

import JNITest
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.snapshot.SnapshotManager
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL

class SnapshotManagerTest : JNITest() {
    private val files = File(ClassLoader.getSystemResources("files").nextElement().let(URL::getFile)).listFiles()

    private val location = files?.find { file -> file.nameWithoutExtension == "video_only" }?.absolutePath!!

    @Test
    fun `single snapshot`() = runTest {
        val snapshot = SnapshotManager.snapshot(
            location = location,
            keyframesOnly = false,
            timestampMillis = { durationMillis ->
                (0L..durationMillis).random()
            }).getOrThrow()
        assertInstanceOf(Frame.Video.Content::class.java, snapshot)
        with(snapshot!!) {
            assertEquals(500, width)
            assertEquals(500, height)
            assertEquals(25.0, frameRate)
        }
    }

    @Test
    fun `multiple snapshots`() = runTest {
        SnapshotManager.snapshots(
            location = location,
            timestampsMillis = { durationMillis ->
                buildList {
                    repeat(10) {
                        add((0L..durationMillis).random())
                    }
                }
            }
        ).getOrThrow().forEach { snapshot ->
            assertInstanceOf(Frame.Video.Content::class.java, snapshot)
            with(snapshot) {
                assertEquals(500, width)
                assertEquals(500, height)
                assertEquals(25.0, frameRate)
            }
        }
    }
}