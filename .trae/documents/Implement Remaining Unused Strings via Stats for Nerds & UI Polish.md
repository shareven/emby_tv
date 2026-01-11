I will implement the remaining unused strings by adding a "Stats for Nerds" feature and refining existing UI components.

### 1. Stats for Nerds (PlayerScreen.kt)
I will add a feature to display technical playback details, utilizing the following unused strings:
- `playback` (Title for the section)
- `direct_play`, `direct_stream`, `transcode` (Playback method)
- `hardware_acceleration` (Transcode info)
- `stereo` (Audio channels)
- `fps_suffix` (Frame rate)
- `unknown` (Fallback)
- `end` (Playback end time, if applicable)

### 2. UI Refinements & Missing Logic
- **`PlayerScreen.kt`**:
    - Use `press_menu_down_to_show_menu` as an alternative or additional hint.
    - Use `disable_subtitles` (alias for `subtitles_off` or distinct action).
    - Use `default_marker` to indicate default tracks in Audio/Subtitle tabs.
    - Use `audio_label` for the Audio tab title if `audio` is used elsewhere, or verify usage.
    - Use `no_episodes_found` in `EpisodesTab` when the list is empty.

- **`LoginScreen.kt`**:
    - Use `local_server_url` to label the discovered server address explicitly.

- **`MediaDetailScreen.kt` / `LibraryScreen.kt`**:
    - Use `counts` (e.g., "5 items") in library view or details.
    - Use `season` (singular) where applicable (e.g., "Season 1" vs "Seasons: 2").

### 3. Cleanup
- Verify usage of `list_loop`, `single_loop`, `no_loop` (they seem to be used in code but marked unused in md? I will double check `PlayerScreen.kt` imports and usage).
- Update `strings_use.md` to reflect all changes.

### 4. Verification
- Build and ensure no errors.
- Verify `strings_use.md` has no "Unused" entries (except `app_name`).