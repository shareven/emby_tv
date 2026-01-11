I will migrate the features from the Flutter project to the Kotlin `PlayerScreen.kt`, focusing on using ExoPlayer for subtitles and replicating the UI.

### 1. ExoPlayer Native Subtitles (Simplification)

* **Remove**: Delete manual subtitle parsing logic (`SubtitleParser`, `fetchSubtitleCues`, `SubtitleCue`, etc.) and the custom subtitle rendering overlay.

* **Implement**: Configure ExoPlayer to handle subtitles natively.

  * For **External Subtitles**: Construct `MediaItem.SubtitleConfiguration` using the Emby subtitle URLs and add them to the `MediaItem`.

  * For **Embedded Subtitles**: Rely on ExoPlayer's automatic detection.

  * **Selection**: Update `trackSelector` parameters to switch languages, instead of manually fetching text.

### 2. UI Feature Parity (Match Flutter)

* **Pause & Info Overlay**:

  * Recreate the Flutter overlay using Jetpack Compose.

  * **Content**: Port the logic to display "Streaming Info" (Container, Bitrate, Transcode Reason), "Video Info" (Resolution, Codec), and "Audio Info".

  * **Layout**: Top-left details, Center Play button, Bottom progress bar and timestamps.

* **Tabbed Menu System**:

  * Replace the simple menu with a **Tabbed Dialog** matching Flutter's `_showAlert`.

  * **Tabs**:

    * **Episodes**: Horizontal list of episodes (for TV Series) using the existing `BuildItem` component.

    * **Info**: Media overview and metadata.

    * **Subtitles & Audio**: Scrollable lists to select tracks via `trackSelector`.

    * **Playback Correction**: Toggle for Transcoding/Direct Play.

    * **Playback**: Loop mode options.

### 3. Implementation Details

* **Logic Porting**: Translate the string formatting helpers (e.g., `_streamContainerLine`, `_videoMainLine`) from Dart to Kotlin to ensure identical information display.

* **Constraints**: Strictly preserve `implementation("androidx.tv:tv-material:1.0.1")` and `import androidx.tv.material3.*` as requested.

