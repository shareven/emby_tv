I have identified the issue:

**Crash Analysis**:
The crash `java.lang.IllegalArgumentException: Navigation destination that matches route media/438 cannot be found in the navigation graph` indicates that `HomeScreen` is trying to navigate to a route starting with `media/` (e.g., `media/438`), but this route is **not defined** in the `NavHost` in `MainActivity.kt`.

**Cause**:
In `HomeScreen.kt`:
*   The "Continue Watching" section uses `navController.navigate("media/$id")`.
*   The "Latest Items" section also uses `navController.navigate("media/$id")`.

However, in `MainActivity.kt`, the defined routes are:
*   `loading`
*   `login`
*   `home`
*   `update`
*   `library/{libraryId}...`
*   `series/{seriesId}`
*   `player/{mediaJson}`

There is NO route defined for `media/{mediaId}`.

**Fix**:
I need to update `HomeScreen.kt` to navigate to the correct route. Based on the logic in `MainActivity.kt`, viewing a media item (like a movie or episode) usually involves checking its type.
*   If it's a series/season, it might go to `series/{seriesId}`.
*   If it's a playable item (Episode or Movie), it likely goes to the player directly OR to a detail screen.
*   The `MainActivity` has a `MediaDetailScreen` mapped to `series/{seriesId}`. It seems `MediaDetailScreen` is intended to handle media details.

Wait, looking at `MainActivity.kt`:
```kotlin
        // 剧集详情页面
        composable(
            "series/{seriesId}",
            arguments = listOf(navArgument("seriesId") { type = NavType.StringType })
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getString("seriesId") ?: ""
            MediaDetailScreen(...)
        }
```
It seems `series/{seriesId}` is used for details. The name "series" might be a misnomer if it handles Movies too, or maybe Movies don't have a detail screen in this current implementation and go straight to player?

Let's check `MediaDetailScreen` usage.
If I look at `HomeScreen.kt`, for `MediaSection` (My Libraries), it navigates to `library/...`.
For items inside libraries (Latest Items, Continue Watching), these are individual media items.

If the item is a Series or Season, it should go to `series/$id`.
If the item is a Movie or Episode, does it have a detail screen?
The crash happens when navigating to `media/438`.

I should probably change the route in `HomeScreen.kt` from `media/$id` to `series/$id` (assuming `MediaDetailScreen` handles generic media details, which is common in simple apps, or I should check if `MediaDetailScreen` is capable of showing movie details).

Let's verify `MediaDetailScreen` capabilities or if I need to add a `media/{mediaId}` route that points to `MediaDetailScreen`.
Given the route name `series/{seriesId}`, it strongly suggests it was built for TV Series.

However, `MediaDetailScreen` likely fetches generic item info.
I will change `HomeScreen.kt` to use `series/$id` instead of `media/$id`, OR add a `media/{mediaId}` route in `MainActivity.kt` that also renders `MediaDetailScreen`.

The safer and more semantic approach for a general media item is to probably use a generic route.
I will add a `media/{mediaId}` route to `MainActivity.kt` that points to `MediaDetailScreen` (just like `series` route), and also ensure `HomeScreen.kt` uses this route.
Actually, `HomeScreen.kt` is *already* trying to use `media/$id`. So the missing piece is just the definition in `MainActivity.kt`.

**Plan**:
1.  **Modify `MainActivity.kt`**:
    *   Add a new `composable("media/{mediaId}")` that renders `MediaDetailScreen`. This will handle the navigation request from `HomeScreen`.
    *   This is safer than renaming the route in `HomeScreen` because `MediaDetailScreen` might be generic enough.

2.  **Verify**:
    *   Ensure `MediaDetailScreen` accepts `seriesId` (which acts as `mediaId`) and loads data correctly. (I'll assume it does based on typical patterns, passing `mediaId` to the `seriesId` parameter of `MediaDetailScreen`).

### Plan Details:
1.  Open `MainActivity.kt`.
2.  Duplicate the `series/{seriesId}` composable block but change the route to `media/{mediaId}` and the argument name to `mediaId`.
3.  Pass `mediaId` to `MediaDetailScreen`'s `seriesId` parameter (assuming it's just an ID loader).

Wait, looking at `MainActivity.kt`:
```kotlin
            MediaDetailScreen(
                seriesId = seriesId,
                ...
            )
```
I'll just map `media/{mediaId}` to this.

**Alternative**: Update `HomeScreen.kt` to use `series/$id` if `MediaDetailScreen` is indeed the intended target and "series" is just a legacy naming.
However, "media" is more appropriate for mixed content (Movies + Series). So adding the route `media/{mediaId}` to `MainActivity` is the cleanest fix to resolve the crash and support the existing code in `HomeScreen`.

**Steps**:
1.  Update `MainActivity.kt` to include the missing route.