I have identified the issue: The `Emby_tvTheme` function in `Theme.kt` is only configuring the standard `androidx.compose.material3.MaterialTheme`, but it is NOT configuring the `androidx.tv.material3.MaterialTheme`.

Since the application is using TV components (like `androidx.tv.material3.Button`, `androidx.tv.material3.Text`, etc.), these components rely on `androidx.tv.material3.MaterialTheme` for their styling, including the `primary` color. Because it's not configured, they are falling back to default TV Material 3 colors (likely a purple/blue default) instead of the Pink color defined in `Color.kt`.

To fix this, I need to update `Theme.kt` to also provide the `androidx.tv.material3.MaterialTheme`.

### Plan:

1.  **Modify `ui/theme/Theme.kt`**:
    *   Import `androidx.tv.material3.MaterialTheme` (aliased as `TvMaterialTheme` to avoid conflict) and `androidx.tv.material3.darkColorScheme` / `lightColorScheme`.
    *   Create a TV-specific color scheme using the same Pink colors (`Pink80`/`Pink40`) as the mobile theme.
    *   Wrap the `content` (or the existing `MaterialTheme`) inside a `TvMaterialTheme` provider. This ensures both mobile and TV components get the correct theming.

2.  **Verify Usage**:
    *   Check `MainActivity.kt` to ensure `Emby_tvTheme` is wrapping the entire app content (it already is).
    *   The `TvMaterialTheme` should be nested or parallel to `MaterialTheme` depending on how components are mixed, but typically wrapping the whole hierarchy with `TvMaterialTheme` inside `Emby_tvTheme` is the correct approach for a TV app.

### Steps:

1.  Edit `app/src/main/java/com/xxxx/emby_tv/ui/theme/Theme.kt` to include `androidx.tv.material3.MaterialTheme` configuration.