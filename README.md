<a href="https://www.buymeacoffee.com/numq"><img src="https://img.buymeacoffee.com/button-api/?text=Buy me a one way ticket&emoji=✈️&slug=numq&button_colour=5F7FFF&font_colour=ffffff&font_family=Inter&outline_colour=000000&coffee_colour=FFDD00" /></a>

<div style="display: flex; justify-content: center; align-items: center">
    <img src="./media/logo.png" alt="logo" style="max-width: 100%; height: 256px; display: inline-block; vertical-align: middle">
    <p style="color: #24242f; display: inline-block; vertical-align: middle; font-size: 128px">Klarity</p>
</div>

Klarity is a library providing media player functionality written in Kotlin for developing Compose Desktop applications

---

# Features

- Media playback (audio, video)
- Change playback speed with time stretching (without affecting pitch)
- Continuous preview for timeline-like features
- Creation of snapshots by timestamps
- Coroutines/Flow based API
- Compose video rendering

# Architecture

## Dependency graph

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

## State diagram

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

## Transition table

| Current State \ Target State | Empty | Preparing | Ready (Stopped) | Ready (Playing) | Ready (Paused) | Ready (Completed) | Ready (Seeking) | Released |
|------------------------------|-------|-----------|-----------------|-----------------|----------------|-------------------|-----------------|----------|
| **Empty**                    | N/A   | Prepare   | N/A             | N/A             | N/A            | N/A               | N/A             | N/A      |
| **Preparing**                | N/A   | N/A       | Media Ready     | N/A             | N/A            | N/A               | N/A             | Release  |
| **Ready (Stopped)**          | N/A   | N/A       | N/A             | Play            | N/A            | N/A               | SeekTo          | Release  |
| **Ready (Playing)**          | N/A   | N/A       | Stop            | N/A             | Pause          | N/A               | SeekTo          | N/A      |
| **Ready (Paused)**           | N/A   | N/A       | Stop            | Resume          | N/A            | N/A               | SeekTo          | N/A      |
| **Ready (Completed)**        | N/A   | N/A       | Stop            | N/A             | N/A            | N/A               | SeekTo          | N/A      |
| **Ready (Seeking)**          | N/A   | N/A       | Stop            | N/A             | Seek Completed | N/A               | SeekTo          | N/A      |

# Supported formats

[Full list of formats supported by FFmpeg](https://ffmpeg.org/ffmpeg-formats.html)

# Modules

## Core

Contains basic components such as: controller, player, preview manager, snapshot manager

### Dependencies

- [klarity-decoder](https://github.com/numq/klarity-decoder)
    - [FFmpeg](https://www.ffmpeg.org/)

- [klarity-sampler](https://github.com/numq/klarity-sampler)
    - [PortAudio](https://github.com/PortAudio/portaudio/)
    - [Signalsmith Audio](https://github.com/Signalsmith-Audio/signalsmith-stretch)

## Compose

Contains a Compose component for rendering video

### Dependencies

- [Core Module](#core)

## Example

Contains a demo application

### Dependencies

- [Core Module](#core)
- [Compose Module](#compose)