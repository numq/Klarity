package com.github.numq.klarity.core.decoder

enum class HardwareAcceleration {
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