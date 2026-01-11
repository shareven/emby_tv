I will implement the season and episode selection in `MediaDetailScreen.kt` similar to the Flutter implementation, and I will also apply the dynamic aspect ratio calculation logic to all 4 requested locations where `BuildItem` is used, ensuring no modification to `BuildItem` itself.

**Plan:**

1.  **Modify `MediaDetailScreen.kt`**:
    *   **Implement Season & Episode List**:
        *   Fetch seasons and episodes data using `appModel` (similar to `_load` in Flutter).
        *   Add a `LaunchedEffect` to load seasons and episodes when `mediaInfo` type is "Series".
        *   Add state variables for `seasons`, `episodes`, and `selectedSeasonIndex`.
        *   Implement a Season Selector (horizontal list of season pills/buttons).
        *   Implement an Episode List (horizontal list of episode cards) that updates based on the selected season.
    *   **Apply Dynamic Aspect Ratio**:
        *   For the Episode List in `MediaDetailScreen`, calculate `imgHeight` and `aspectRatio` using the provided logic:
            ```kotlin
            val aspectRatios = items.map { (it["PrimaryImageAspectRatio"] as? Number)?.toFloat() ?: 1f }
            val maxAspectRatio = aspectRatios.maxOrNull() ?: 1f
            var imgHeight = 0.dp
            if (maxAspectRatio >= 1f) {
                imgHeight = (220.dp.value / maxAspectRatio).dp // Using 220.dp as maxLength example, adjustable
            } else {
                imgHeight = 220.dp
            }
            ```
        *   For the Cast List (People) in `MediaDetailScreen`, apply similar logic (though cast usually has standard ratio, dynamic calculation makes it robust).

2.  **Modify `PlayerScreen.kt`**:
    *   **Apply Dynamic Aspect Ratio**:
        *   In `EpisodesTab`, apply the same aspect ratio calculation logic for the list of episodes shown in the player menu.

3.  **Modify `LibraryScreen.kt`**:
    *   **Apply Dynamic Aspect Ratio**:
        *   In the `TvLazyVerticalGrid` item builder, calculate the aspect ratio and height dynamically for library items. Since it's a grid, we might need to unify the height per row or for the whole grid to avoid misalignment, or just apply it per item if `BuildItem` handles it well (it wraps content). The requirement says "modify ... like this", so I will apply the calculation logic.

**Summary of Changes:**
*   **`MediaDetailScreen.kt`**: Add Season/Episode logic + Dynamic Aspect Ratio for Episodes & Cast.
*   **`PlayerScreen.kt`**: Dynamic Aspect Ratio for Episodes list.
*   **`LibraryScreen.kt`**: Dynamic Aspect Ratio for Library grid items.

I will use `maxLength = 220.dp` (or similar appropriate value from `HomeScreen`) as the base length for the calculation.

**Step-by-step Implementation:**
1.  Update `MediaDetailScreen.kt` to include Season/Episode fetching and UI, and apply dynamic sizing.
2.  Update `PlayerScreen.kt` to apply dynamic sizing in `EpisodesTab`.
3.  Update `LibraryScreen.kt` to apply dynamic sizing in `LibraryScreen`.