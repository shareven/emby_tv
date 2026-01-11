I have identified the issues:

1.  **"Latest Media Library List Not Loading" (最近的媒体库列表没加载出来)**:
    *   **Cause**: In `EmbyService.kt`, the `getLatestItems` function is incorrect. It simply returns `getViews(userId)` without actually fetching the latest items for each view (library). The Flutter implementation iterates through each view and calls `getLatestItemsByViews`.
    *   **Fix**: Update `EmbyService.kt` to iterate through the views, fetch latest items for each, and attach them to the view object (similar to Flutter).

2.  **"Crash on Entering Player Screen" (进入播放页闪退)**:
    *   **Cause**:
        1.  In `PlayerScreen.kt`, accessing `MediaSources` uses `get(0)` on a list that might be empty or null, which can cause an `IndexOutOfBoundsException`.
        2.  In `MainActivity.kt`, `playbackPositionTicks` (Long) is cast to `Int`, which is unnecessary and potentially risky (though unlikely to crash unless overflow happens, but best practice is to keep it Long).
    *   **Fix**:
        1.  In `PlayerScreen.kt`, use `firstOrNull()` instead of `get(0)` for safe list access.
        2.  Update `PlayerScreen` to accept `playbackPositionTicks` as `Long`.
        3.  Update `MainActivity.kt` to pass `Long`.

### Plan:

1.  **Update `EmbyService.kt`**:
    *   Rewrite `getLatestItems` to fetch views, then iterate and fetch `getLatestItemsByViews` for each view, attaching the result as a `latestItems` JsonArray to the view JsonObject.

2.  **Update `PlayerScreen.kt`**:
    *   Change `playbackPositionTicks` parameter type from `Int` to `Long`.
    *   Replace `(mediaMap["MediaSources"] as? List<*>)?.get(0)` with `?.firstOrNull()`.
    *   Replace `(media["MediaSources"] as? List<*>)?.get(0)` with `?.firstOrNull()` in other places (StatsOverlay, etc.).

3.  **Update `MainActivity.kt`**:
    *   Pass `playbackPositionTicks` as `Long` to `PlayerScreen`.