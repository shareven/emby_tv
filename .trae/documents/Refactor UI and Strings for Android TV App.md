# Comprehensive UI and String Refactoring Plan

## 1. String Resources Update

Add missing strings to `values/strings.xml` and `values-zh/strings.xml` to support all screens and replace hardcoded text.

**New Strings to Add:**

* **Login/Server**: `scan_qr_hint`, `starting_server`, `local_server_url, `**footer\_notice**

* **Player Menu**: `info`, `episodes`, `subtitles`, `audio`, `playback_correction`, `play_mode`, `close`, `not_series_content`

* **Player Options**: `subtitles_off`, `off_default`, `server_transcode`, `loop_list`, `loop_single`, `loop_off`

* **Update Screen**: `update_title`, `update_completed`, `update_install_hint`, `update_failed`, `update_retry`, `update_downloading`, `update_start_failed`

## 2. Screen Refactoring

### UpdateScreen.kt

* Replace all hardcoded Chinese strings with `stringResource`.

* Apply `GradientBackground` for visual consistency.

### PlayerScreen.kt

* Verify and use the newly added string resources.

* Ensure the menu dialog matches the app's dark/TV theme.

### MediaDetailScreen.kt

* Replace the placeholder "Cover Image" `Card` with `AsyncImage` to display the actual poster.

* Ensure `GradientBackground` is applied.

### LibraryScreen.kt

* Update navigation to pass the Library Name (Title) so the screen displays "Movies" or "TV Shows" instead of a static "My Libraries".

* Apply `GradientBackground`.

### HomeScreen.kt

* Refactor `TopStatusBar` and `MediaSection` to use theme colors instead of hardcoded White/Black.

* Ensure `GradientBackground` covers the full screen.

### LoginScreen.kt

* Final polish of the QR code section with localized strings.

## 3. Local Server HTML Styling

* Update `LocalServer.kt` to generate a styled HTML page.

* Use CSS to match the app's blue/purple gradient theme (`#448AFF` to `#E040FB`).

* Ensure the form is centered, responsive, and looks professional on mobile browsers.

## 4. Navigation Update

* Modify `MainActivity.kt` route for `library` to accept a `title` argument.

* Update `HomeScreen.kt` to pass the title when clicking a library.

* Update `LibraryScreen.kt` to accept and display the title.

