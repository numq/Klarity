<a href="https://www.buymeacoffee.com/numq"><img src="https://img.buymeacoffee.com/button-api/?text=Buy me a one way ticket&emoji=✈️&slug=numq&button_colour=5F7FFF&font_colour=ffffff&font_family=Inter&outline_colour=000000&coffee_colour=FFDD00" /></a>

<div align="center">
<h1>Klarity</h1>
<img src="media/logo.png" alt="logo" height="128px"/>
</div>

### Klarity is an FFMpeg-based media player library written in C++ and Kotlin, and supports use in Jetpack Compose Desktop projects.

<div align="center">
<img src="media/screenshot.png" alt="screenshot"/>
</div>

---

## Table of Content

* [Features](#features)
* [Architecture](#architecture)
    * [Dependency graph](#dependency-graph)
    * [State diagram](#state-diagram)
    * [Transition table](#transition-table)
* [Supported formats](#supported-formats)
* [Modules](#modules)
    * [Core](#core)
    * [Compose](#compose)
    * [Example](#example)
* [Components](#components)
* [Installation](#installation-and-usage)

# Features

- Media playback (audio, video)
- Change playback speed with time stretching (without affecting pitch)
- Continuous preview for timeline-like features
- Creation of snapshots by timestamps
- Coroutines/Flow based API
- Compose video rendering

## Architecture

### Dependency graph

```mermaid
graph TD
    KlarityPlayer --> PlayerController
    PlayerController --> Pipeline
    PlayerController --> BufferLoop
    PlayerController --> PlaybackLoop
    PlayerController --> Settings
    PlayerController --> PlayerState
    PlayerController --> BufferTimestamp
    PlayerController --> PlaybackTimestamp
    PlayerController --> Renderer
    PlayerController --> Events
    BufferLoop --> Pipeline
    PlaybackLoop --> BufferLoop
    PlaybackLoop --> Pipeline
    PlayerController --> Pipeline
    subgraph Pipeline
        Pipeline.AudioVideo --> Media
        Pipeline.AudioVideo --> AudioDecoder
        Pipeline.AudioVideo --> VideoDecoder
        Pipeline.AudioVideo --> AudioBuffer
        Pipeline.AudioVideo --> VideoBuffer
        Pipeline.AudioVideo --> Sampler
        Pipeline.AudioVideo --> Renderer
        Pipeline.Audio --> Media
        Pipeline.Audio --> AudioDecoder
        Pipeline.Audio --> AudioBuffer
        Pipeline.Audio --> Sampler
        Pipeline.Video --> Media
        Pipeline.Video --> VideoDecoder
        Pipeline.Video --> VideoBuffer
        Pipeline.Video --> Renderer
    end
    Sampler --> JNI\nNativeSampler --> C++\nSampler
    AudioDecoder --> JNI\nNativeDecoder
    VideoDecoder --> JNI\nNativeDecoder
    JNI\nNativeDecoder --> C++\nDecoder
```

### State diagram

```mermaid
stateDiagram-v2
    state PlayerState {
        [*] --> Empty
        Empty --> Preparing: Prepare Media
        Preparing --> Ready: Media Ready
        Preparing --> Empty: Release

        state Ready {
            [*] --> Stopped
            Stopped --> Playing: Play
            Playing --> Paused: Pause
            Playing --> Stopped: Stop
            Playing --> Seeking: SeekTo
            Paused --> Playing: Resume
            Paused --> Stopped: Stop
            Paused --> Seeking: SeekTo
            Stopped --> Completed: Playback Completed
            Stopped --> Seeking: SeekTo
            Completed --> Stopped: Stop
            Completed --> Seeking: SeekTo
            Seeking --> Seeking: SeekTo
            Seeking --> Paused: Seek Completed
            Seeking --> Stopped: Stop
        }

        Ready --> Empty: Release
    }
```

### Transition table

| Current State \ Target State | Empty | Preparing | Ready (Stopped) | Ready (Playing) | Ready (Paused) | Ready (Completed) | Ready (Seeking) | Released |
|------------------------------|-------|-----------|-----------------|-----------------|----------------|-------------------|-----------------|----------|
| **Empty**                    | N/A   | Prepare   | N/A             | N/A             | N/A            | N/A               | N/A             | N/A      |
| **Preparing**                | N/A   | N/A       | Media Ready     | N/A             | N/A            | N/A               | N/A             | Release  |
| **Ready (Stopped)**          | N/A   | N/A       | N/A             | Play            | N/A            | N/A               | SeekTo          | Release  |
| **Ready (Playing)**          | N/A   | N/A       | Stop            | N/A             | Pause          | N/A               | SeekTo          | N/A      |
| **Ready (Paused)**           | N/A   | N/A       | Stop            | Resume          | N/A            | N/A               | SeekTo          | N/A      |
| **Ready (Completed)**        | N/A   | N/A       | Stop            | N/A             | N/A            | N/A               | SeekTo          | N/A      |
| **Ready (Seeking)**          | N/A   | N/A       | Stop            | N/A             | Seek Completed | N/A               | SeekTo          | N/A      |

## Supported formats

[Full list of formats supported by FFmpeg](https://ffmpeg.org/ffmpeg-formats.html)

## Modules

### Core

> Contains basic components such as: controller, player, preview manager, snapshot manager

#### Dependencies

- [klarity-decoder](https://github.com/numq/klarity-decoder)
    - [FFmpeg](https://www.ffmpeg.org/)

- [klarity-sampler](https://github.com/numq/klarity-sampler)
    - [PortAudio](https://github.com/PortAudio/portaudio/)
    - [Signalsmith Audio](https://github.com/Signalsmith-Audio/signalsmith-stretch)

### Compose

> Contains a Compose component for rendering video

#### Dependencies

- [Core Module](#core)

### Example

> Contains a demo application

#### Dependencies

- [Core Module](#core)
- [Compose Module](#compose)

To run demo application place the binaries appropriate to your platform into the `example/bin` folder and run
the [Application](example/src/main/kotlin/application/Application.kt).

### Components

- [KlarityPlayer](core/src/main/kotlin/com/github/numq/klarity/core/player/KlarityPlayer.kt) - contains the media player
  logic.
- [SnapshotManager](core/src/main/kotlin/com/github/numq/klarity/core/snapshot/SnapshotManager.kt) - allows to get
  frames (snapshots) with a
  specified timestamp once per session. Use case - previewing keyframes.
- [PreviewManager](core/src/main/kotlin/com/github/numq/klarity/core/preview/PreviewManager.kt) - allows to get frames (
  snapshots) with a
  specific timestamp during a continuous session. Use case - previewing the timeline.
- [Renderer](compose/src/main/kotlin/com/github/numq/klarity/compose/renderer/Renderer.kt) - Provides functionality for
  rendering background and
  foreground.

### Installation and usage

> [!IMPORTANT]
> Binaries must be located in the filesystem, however, they can be part of a jar - for this the binaries must be
> opened as temporary files whose path can be used by the loader.

- Include the library jars in project
- Place binaries in folder
- Load binaries by specifying path in calls to `loadDecoder` and `loadSampler` functions
  of [Klarity](core/src/main/kotlin/com/github/numq/klarity/core/loader/Klarity.kt) loader object
- Instantiate and use **core** media player components located in `com.github.numq.klarity.core`
- Instantiate and use specific **Jetpack Compose** components located in `com.github.numq.klarity.compose`