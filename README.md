[![Buy Me a Coffee](https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20coffee&emoji=☕&slug=numq&button_colour=17517e&font_colour=ffffff&font_family=Inter&outline_colour=000000&coffee_colour=FFDD00)](https://coff.ee/numq)

<div align="center">
<h1>Klarity</h1>
<img src="media/logo.png" alt="logo" height="128px"/>
</div>

**Klarity** is a media (video and audio) player for **Compose Multiplatform** (desktop-only), built on top of the
native **FFMpeg** and **PortAudio** libraries, and rendered using the **Skiko** library.

Since frames are rendered directly into the `Composable`, this eliminates the need for compatibility components like
`SwingPanel`, making it possible to display any `Composable` as an overlay on top of a frame.

<br>

<div align="center">
<img src="media/preview.png" alt="preview"/>
</div>

## Table of Content

* [Changelog](#changelog)
* [Supported operating systems](#supported-operating-systems)
* [Features](#features)
* [Architecture](#architecture)
    * [Dependency graph](#dependency-graph)
    * [State diagram](#state-diagram)
    * [Transition table](#transition-table)
* [Installation](#installation)
* [Usage](#usage)
* [Used libraries](#used-libraries)

# Changelog

## [1.0.1](https://github.com/numq/Klarity/releases/tag/1.0.1)

### Changed:

- Improved internal state machine
- Improved playback synchronization
- Improved seeking

## [1.0.0](https://github.com/numq/Klarity/releases/tag/1.0.0)

- Stable release

# Supported operating systems

- Windows x64
- Linux x64
- macOS x64

# Features

- Media files probing
- Audio and video playback of media files
- Slow down and speed up playback speed without changing pitch
- Getting a preview of a media file
- Getting frames (snapshots) of a media file
- Coroutine/Flow API

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
    PlaybackLoop --> Renderer
    PlaybackLoop --> Settings
    subgraph Pipeline
        Pipeline.AudioVideo --> Media
        Pipeline.AudioVideo --> AudioDecoder
        Pipeline.AudioVideo --> VideoDecoder
        Pipeline.AudioVideo --> AudioBuffer
        Pipeline.AudioVideo --> VideoBuffer
        Pipeline.AudioVideo --> Sampler
        Pipeline.AudioVideo --> VideoPool
        Pipeline.Audio --> Media
        Pipeline.Audio --> AudioDecoder
        Pipeline.Audio --> AudioBuffer
        Pipeline.Audio --> Sampler
        Pipeline.Video --> Media
        Pipeline.Video --> VideoDecoder
        Pipeline.Video --> VideoBuffer
        Pipeline.Video --> VideoPool
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
        Preparing --> Error: Error Occurred
        Preparing --> Empty: Release

        state Ready {
            [*] --> Stopped
            Stopped --> Playing: Play
            Playing --> Paused: Pause
            Playing --> Stopped: Stop
            Playing --> Seeking: SeekTo
            Playing --> Error: Error Occurred
            Paused --> Playing: Resume
            Paused --> Stopped: Stop
            Paused --> Seeking: SeekTo
            Paused --> Error: Error Occurred
            Stopped --> Completed: Playback Completed
            Stopped --> Seeking: SeekTo
            Stopped --> Error: Error Occurred
            Completed --> Stopped: Stop
            Completed --> Seeking: SeekTo
            Completed --> Error: Error Occurred
            Seeking --> Paused: Seek Completed
            Seeking --> Stopped: Stop
            Seeking --> Seeking: SeekTo
            Seeking --> Error: Error Occurred
        }

        Ready --> Empty: Release
        Ready --> Error: Error Occurred
        Error --> Empty: Reset
    }
```

### Transition table

| Current State \ Target State | Empty    | Preparing | Ready (Stopped) | Ready (Playing) | Ready (Paused)  | Ready (Completed)   | Ready (Seeking) | Error          |
|------------------------------|----------|-----------|-----------------|-----------------|-----------------|---------------------|-----------------|----------------|
| **Empty**	                   | N/A	     | Prepare   | 	N/A            | 	N/A            | 	N/A	           | N/A	                | N/A             | 	N/A           |
| **Preparing**	               | Release	 | N/A	      | Media Ready     | 	N/A	           | N/A	            | N/A	                | N/A	            | Error Occurred |
| **Ready (Stopped)**	         | Release	 | N/A	      | N/A	            | Play	           | N/A	            | Playback Completed	 | SeekTo	         | Error Occurred |
| **Ready (Playing)**	         | N/A	     | N/A	      | Stop	           | N/A	            | Pause	          | N/A	                | SeekTo	         | Error Occurred |
| **Ready (Paused)**	          | N/A	     | N/A	      | Stop	           | Resume	         | N/A	            | N/A	                | SeekTo	         | Error Occurred |
| **Ready (Completed)**	       | N/A	     | N/A	      | Stop	           | N/A	            | N/A	            | N/A	                | SeekTo	         | Error Occurred |
| **Ready (Seeking)**	         | N/A	     | N/A	      | Stop	           | N/A	            | Seek Completed	 | N/A	                | SeekTo	         | Error Occurred |
| **Error**	                   | Reset	   | N/A	      | N/A	            | N/A	            | N/A	            | N/A	                | N/A	            | N/A            |

## Installation

Download the [latest release](https://github.com/numq/Klarity/releases/tag/1.0.1) and include jar files to your project
depending on your system.

## Usage

> [!NOTE]
> Check out the [example](example/composeApp/src/commonMain/kotlin/io/github/numq/example) to see a full implementation
> in Clean Architecture using the [Reduce & Conquer](https://github.com/numq/reduce-and-conquer) pattern.

### Load library

- The `KlarityPlayer.load()` method should be called once during the application lifecycle

```kotlin
KlarityPlayer.load().onFailure { t -> }.getOrThrow()
```

### Get probe (information about a [media](library/src/main/kotlin/io/github/numq/klarity/media/Media.kt))

```kotlin
val media = ProbeManager.probe("path/to/media").onFailure { t -> }.getOrThrow()
```

### Get video frames (snapshots)

> [!IMPORTANT]
> [Snapshot](library/src/main/kotlin/io/github/numq/klarity/snapshot/Snapshot.kt) must be closed using the `close()`
> method.

```kotlin
val snapshots = SnapshotManager.snapshots("path/to/media") { timestamps }.onFailure { t -> ... }.getOrThrow()

snapshots.forEach { snapshot ->
    snapshot.close().onFailure { t -> }.getOrThrow()
}

val snapshot = SnapshotManager.snapshot("path/to/media") { timestamp }.onFailure { t -> ... }.getOrThrow()

snapshot.close().onFailure { t -> }.getOrThrow()
```

## Get preview frames (for example, for the timeline)

> [!IMPORTANT]
> [PreviewManager](library/src/main/kotlin/io/github/numq/klarity/preview/PreviewManager.kt) must be closed using the
`close()` method.

```kotlin
val previewManager = PreviewManager.create("path/to/media").onFailure { t -> ... }.getOrThrow()

previewManager.render(renderer, timestamp).onFailure { t -> }.getOrThrow()

previewManager.close().onFailure { t -> }.getOrThrow()
```

### Get media file played

> [!IMPORTANT]
> [KlarityPlayer](library/src/main/kotlin/io/github/numq/klarity/player/KlarityPlayer.kt)
> and [Renderer](library/src/main/kotlin/io/github/numq/klarity/renderer/Renderer.kt) must be closed using the
`close()` method

```kotlin
val playback = KlarityPlayer.create().onFailure { t -> }.getOrThrow()

val format = checkNotNull(playback.state.media.videoFormat)

val renderer = Renderer.create(format).onFailure { t -> }.getOrThrow()

playback.attach(renderer).getOrThrow()

playback.prepare("path/to/media").onFailure { t -> }.getOrThrow()

playback.play().onFailure { t -> }.getOrThrow()

playback.stop().onFailure { t -> }.getOrThrow()

playback.close().onFailure { t -> }.getOrThrow()

renderer.close().onFailure { t -> }.getOrThrow()
```

## Used libraries

- [FFMPEG](https://ffmpeg.org/)
- [PortAudio](https://www.portaudio.com/)
- [Signalsmith Stretch](https://github.com/Signalsmith-Audio/signalsmith-stretch/)
- [Skiko](https://github.com/JetBrains/skiko/)