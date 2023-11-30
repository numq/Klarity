package converter

import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

class ByteArrayFrameConverter : AutoCloseable, FrameConverter<ByteArray>() {

    /**
     * No need for conversion
     */
    override fun convert(bytes: ByteArray?) = throw Exception("Not supported")

    /**
     * Convert AUDIO or VIDEO based on frame type
     */
    override fun convert(frame: Frame) = when (frame.type) {
        Frame.Type.AUDIO -> ::getDecodedSamples
        Frame.Type.VIDEO -> ::getDecodedImage
        else -> null
    }?.invoke(frame)

    /**
     * S16LE audio bytes conversion
     */
    private fun getDecodedSamples(frame: Frame) = runCatching {
        frame.takeIf { it.type == Frame.Type.AUDIO && it.samples != null }?.use { frame ->
            with(frame) {
                (samples?.firstOrNull() as? ShortBuffer)?.run {

                    if (audioChannels <= 0 || sampleRate <= 0) return null

                    val dataSize = remaining() * audioChannels

                    val bytes = ByteArray(dataSize)

                    rewind()

                    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(this)

                    bytes
                }
            }
        }
    }.onFailure { println(it.localizedMessage) }.getOrNull()

    /**
     * BGRA image bytes conversion
     */
    private fun getDecodedImage(frame: Frame) = runCatching {
        frame.takeIf { it.type == Frame.Type.VIDEO && it.image != null }?.use { frame ->
            with(frame) {
                (image?.firstOrNull() as? ByteBuffer)?.run {

                    if (imageDepth != Frame.DEPTH_UBYTE && imageDepth != Frame.DEPTH_BYTE) return null

                    if (imageHeight <= 0 || imageWidth <= 0) return null

                    val dataSize = imageWidth * imageHeight * imageChannels

                    val bytes = ByteArray(dataSize)

                    rewind()

                    for (y in 0 until imageHeight) {
                        for (x in 0 until imageWidth) {

                            val srcIndex = y * imageStride + x * 4

                            val blue = get(srcIndex).toInt() and 0xFF
                            val green = get(srcIndex + 1).toInt() and 0xFF
                            val red = get(srcIndex + 2).toInt() and 0xFF
                            val alpha = get(srcIndex + 3).toInt() and 0xFF

                            val dstIndex = (y * imageWidth + x) * 4

                            bytes[dstIndex] = blue.toByte()
                            bytes[dstIndex + 1] = green.toByte()
                            bytes[dstIndex + 2] = red.toByte()
                            bytes[dstIndex + 3] = alpha.toByte()
                        }
                    }
                    bytes
                }
            }
        }
    }.onFailure { println(it.localizedMessage) }.getOrNull()

    override fun close() = super.close()
}