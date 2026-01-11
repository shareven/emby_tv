I will refactor the codebase to align with Android standards and the specific requirements provided.

### 1. Refactor AppModel.kt
- **Use `viewModelScope.launch`**: Convert public action methods (e.g., `checkUpdate`, `loadCredentials`, `loadData`, `loadSeasons`, `loadEpisodes`, `loadLibraryItems`, `loadResumeItems`, `loadLatestItems`, `login`) from `suspend` functions to regular functions that launch coroutines using `viewModelScope`.
- **String Resources**: Use `getApplication<Application>().getString(R.string.xxx)` for any string retrieval within the ViewModel (non-UI context).
- **Implementation Fixes**: Ensure all necessary data fetching methods (`getSeasonList`, `getSeriesList`, etc.) are correctly implemented calling `EmbyService`.

### 2. Refactor UI Components (Localization)
- **Replace LocalizationHelper**: In all `@Composable` functions (`HomeScreen`, `PlayerScreen`, `MediaDetailScreen`, `LibraryScreen`, `LoginScreen`), replace calls to `LocalizationHelper` with standard `stringResource(id = R.string.xxx)`.
- **Resolve Imports**: Fix ambiguous imports in `PlayerScreen.kt` (specifically `Icon` and `MaterialTheme`) by prioritizing `androidx.tv.material3` or using aliases. Ensure generic components like `Loading`, `NoData`, `GradientBackground` are correctly imported.

### 3. Cleanup
- **Delete File**: Remove `LocalizationHelper.kt` as it is being replaced by standard Android resources.

### 4. Build and Verify
- **Compile**: Run `./gradlew assembleDebug` to ensure all compilation errors (unresolved references, type mismatches) are resolved.
