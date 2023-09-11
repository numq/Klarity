package extension

import org.bytedeco.javacv.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

fun Frame.pixelBytes(): ByteArray? = runCatching {
    val buffer = (image?.firstOrNull() as? ByteBuffer) ?: throw Exception("Unable to get pixels")
    buffer.rewind()

    val bytes = ByteArray(imageWidth * imageHeight * imageChannels)

    for (h in 0 until imageHeight) {
        for (w in 0 until imageWidth) {
            for (c in 0 until imageChannels) {
                bytes[(h * imageWidth + w) * imageChannels + c] = buffer.get()
            }
        }
    }

    buffer.clear()

    return bytes
}.onFailure { println(it.localizedMessage) }.getOrNull()

fun Frame.sampleBytes(): ByteArray? = runCatching {
    val input = (samples?.firstOrNull() as? ShortBuffer) ?: throw Exception("Unable to get samples")
    input.rewind()

    val output = ByteBuffer.allocate(input.capacity() * audioChannels).order(ByteOrder.LITTLE_ENDIAN)

    repeat(input.capacity()) { offset ->
        output.putShort(input.get(offset))
    }

    val bytes = output.array()

    input.clear()
    output.clear()

    return bytes
}.onFailure { println(it.localizedMessage) }.getOrNull()