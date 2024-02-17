package audio

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class NativeAudioSampler(private val nativeAudio: NativeAudio) : AudioSampler {

    private val lock = ReentrantLock()

    private var currentPlaybackSpeed: Float = 1f

    private var currentVolume: Float = 1f

    override fun setPlaybackSpeed(factor: Float) = lock.withLock {
        runCatching {
            if (nativeAudio.setPlaybackSpeed(factor)) {
                currentPlaybackSpeed = factor
                currentPlaybackSpeed
            } else null
        }.onFailure { println(it.localizedMessage) }.getOrNull()
    }

    override fun setVolume(value: Float) = lock.withLock {
        runCatching {
            if (nativeAudio.setVolume(value)) {
                currentVolume = value
                currentVolume
            } else null
        }.onFailure { println(it.localizedMessage) }.getOrNull()
    }

    override fun setMuted(state: Boolean) = lock.withLock {
        runCatching {
            if (nativeAudio.setVolume(if (state) 0f else currentVolume)) state else false
        }.onFailure { println(it.localizedMessage) }.getOrNull()
    }

    override fun init(bitsPerSample: Int, sampleRate: Int, channels: Int) = lock.withLock {
        runCatching {
            nativeAudio.init(bitsPerSample, sampleRate, channels)
        }.onFailure { println(it.localizedMessage) }.getOrDefault(false)
    }

    override fun play(bytes: ByteArray) = lock.withLock {
        runCatching {
            nativeAudio.play(bytes, bytes.size)
        }.onFailure { println(it.localizedMessage) }.getOrDefault(false)
    }

    override fun pause() = lock.withLock {
        runCatching {
            nativeAudio.pause()
        }.onFailure { println(it.localizedMessage) }.getOrDefault(Unit)
    }

    override fun resume() = lock.withLock {
        runCatching {
            nativeAudio.resume()
        }.onFailure { println(it.localizedMessage) }.getOrDefault(Unit)
    }

    override fun stop() = lock.withLock {
        runCatching {
            nativeAudio.stop()
        }.onFailure { println(it.localizedMessage) }.getOrDefault(Unit)
    }

    override fun close() = lock.withLock {
        runCatching {
            nativeAudio.close()
        }.onFailure { println(it.localizedMessage) }.getOrDefault(Unit)
    }
}