#ifndef KLARITY_HWACCEL_H
#define KLARITY_HWACCEL_H

#include "libavutil/hwcontext.h"

enum class HardwareAcceleration {
    NONE = AV_HWDEVICE_TYPE_NONE,
    VIDEOTOOLBOX = AV_HWDEVICE_TYPE_VIDEOTOOLBOX, // macos
    D3D11VA = AV_HWDEVICE_TYPE_D3D11VA, // windows
    D3D12VA = AV_HWDEVICE_TYPE_D3D12VA, // windows
    CUDA = AV_HWDEVICE_TYPE_CUDA, // windows, linux
    QSV = AV_HWDEVICE_TYPE_QSV, // windows, linux
    OPENCL = AV_HWDEVICE_TYPE_OPENCL, // windows, linux, macos
};

#endif //KLARITY_HWACCEL_H
