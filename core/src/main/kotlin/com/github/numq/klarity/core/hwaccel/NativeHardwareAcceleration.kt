package com.github.numq.klarity.core.hwaccel

internal enum class NativeHardwareAcceleration {
    NONE,
    VDPAU,
    CUDA,
    VAAPI,
    DXVA2,
    QSV,
    VIDEOTOOLBOX,
    D3D11VA,
    DRM,
    OPENCL,
    MEDIACODEC,
    VULKAN,
    D3D12VA,
}