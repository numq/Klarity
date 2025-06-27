//package snapshot
//
//import io.github.numq.klarity.snapshot.Snapshot
//import io.github.numq.klarity.snapshot.SnapshotManager
//import kotlinx.coroutines.sync.Mutex
//import kotlinx.coroutines.sync.withLock
//
//interface SnapshotRepository {
//    val snapshots: Map<String, Snapshot>
//
//    suspend fun addSnapshot(location: String): Result<Unit>
//
//    suspend fun getSnapshot(location: String): Result<Snapshot?>
//
//    suspend fun removeSnapshot(location: String): Result<Unit>
//
//    suspend fun close(): Result<Unit>
//
//    class Implementation : SnapshotRepository {
//        private val mutex = Mutex()
//
//        override val snapshots = mutableMapOf<String, Snapshot>()
//
//        override suspend fun addSnapshot(location: String) = mutex.withLock {
//            runCatching {
//                val snapshot = SnapshotManager.snapshot(location = location).getOrThrow()
//
//                if (snapshot != null) {
//                    snapshots[location] = snapshot
//                }
//            }
//        }
//
//        override suspend fun getSnapshot(location: String) = mutex.withLock {
//            runCatching {
//                snapshots[location]
//            }
//        }
//
//        override suspend fun removeSnapshot(location: String) = mutex.withLock {
//            runCatching {
//                snapshots.remove(location)?.close() ?: Unit
//            }
//        }
//
//        override suspend fun close() = mutex.withLock {
//            runCatching {
//                snapshots.values.toList().forEach { snapshot ->
//                    snapshot.close()
//                }
//            }
//        }
//    }
//}