I will perform a comprehensive audit of string usage and improve UI color contrast across the application.

### 1. Generate String Usage Report

* Create `strings_use.md` in the root directory.

* List all keys from `strings.xml`.

* For each key, list the file(s) where it is used (using `grep` results) and not in dart file. 

* Mark unused keys clearly.

### 2. UI Color & Contrast Improvements

* **`LoginScreen.kt`**:

  * Modify `TvInputButton` to ensure text is visible on the gradient background. Likely use `Color.White` or a semi-transparent white background for the input container.

  * Check all Text contrast.

* **`TvInputDialog.kt`**:

  * Ensure the dialog background and text colors are compatible (e.g., dark surface with light text).

* **`UpdateScreen.kt`**:

  * Verify text colors against the gradient background.

* **`PlayerScreen.kt`**:

  * Check the overlay text visibility against the video/dark overlay.

* **`Common.kt`** (`NoData`, `Loading`):

  * Ensure visibility on both dark and light themes (default to dark for TV).

### 3. Implement Unused Strings (Iterative)

* **Phase 1 (Immediate)**:

  * **Login States**: Use `logging_in` and `login_failed` in `LoginScreen` (currently likely using hardcoded or no text for these states).

  * **Player Errors**: Use `failed_get_playback_info` in `PlayerScreen` error handling.

  * **Transcode Reasons**: Implement a method in `Utils` or `PlayerScreen` to display transcode reasons using the `transcode_reason_*` keys (even if just logging or a toast for now).

  * direct\_play streaming ....

  * **Media Details**: Use `details`, `path`, `provider_ids` in `MediaDetailScreen` if applicable.

  * **Season/Episodes**: Use `season`, `seasons` in `MediaDetailScreen` or `PlayerScreen`.

* **Phase 2 (Cleanup)**:

  * Remove duplicate/conflicting keys (e.g., `list_loop` vs `loop_list`) after verifying usage.

  * Update `strings_use.md` to reflect these changes.

### 4. Verification

* Build the app (`./gradlew assembleDebug`).

* Verify `strings_use.md` is accurate.

* Confirm UI changes visually (via code review of colors).

