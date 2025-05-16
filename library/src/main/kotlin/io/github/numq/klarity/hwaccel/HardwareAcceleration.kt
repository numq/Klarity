package io.github.numq.klarity.hwaccel

import io.github.numq.klarity.decoder.NativeDecoder

sealed class HardwareAcceleration private constructor(internal val native: NativeHardwareAcceleration) {
    data object None : HardwareAcceleration(NativeHardwareAcceleration.NONE)

    data object VDPAU : HardwareAcceleration(NativeHardwareAcceleration.VDPAU)

    data object CUDA : HardwareAcceleration(NativeHardwareAcceleration.CUDA)

    data object VAAPI : HardwareAcceleration(NativeHardwareAcceleration.VAAPI)

    data object DXVA2 : HardwareAcceleration(NativeHardwareAcceleration.DXVA2)

    data object QSV : HardwareAcceleration(NativeHardwareAcceleration.QSV)

    data object VideoToolbox : HardwareAcceleration(NativeHardwareAcceleration.VIDEOTOOLBOX)

    data object D3D11VA : HardwareAcceleration(NativeHardwareAcceleration.D3D11VA)

    data object DRM : HardwareAcceleration(NativeHardwareAcceleration.DRM)

    data object OpenCL : HardwareAcceleration(NativeHardwareAcceleration.OPENCL)

    data object MediaCodec : HardwareAcceleration(NativeHardwareAcceleration.MEDIACODEC)

    data object Vulkan : HardwareAcceleration(NativeHardwareAcceleration.VULKAN)

    data object D3D12VA : HardwareAcceleration(NativeHardwareAcceleration.D3D12VA)

    companion object {
        private val values: List<HardwareAcceleration> by lazy {
            listOf(
                None, VDPAU, CUDA, VAAPI, DXVA2, QSV, VideoToolbox, D3D11VA, DRM, OpenCL, MediaCodec, Vulkan, D3D12VA
            )
        }

        internal fun fromNative(nativeHardwareAcceleration: Int) =
            NativeHardwareAcceleration.entries.getOrNull(nativeHardwareAcceleration)?.let { native ->
                values.find { it.native == native }
            } ?: None

        /**
         * Retrieves a list of available hardware acceleration methods for video decoding.
         *
         * @return A [Result] containing a list of supported [HardwareAcceleration] types.
         */
        fun availableHardwareAcceleration() = runCatching {
            NativeDecoder.getAvailableHardwareAcceleration().map { nativeHardwareAcceleration ->
                NativeHardwareAcceleration.entries.getOrNull(nativeHardwareAcceleration)?.let { native ->
                    values.find { it.native == native }
                }
            }.filterNotNull()
        }
    }
}
