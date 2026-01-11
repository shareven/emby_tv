I will refactor the state management to use `AppModel` as the single source of truth and `viewModelScope` for data fetching.

### 1. Refactor `AppModel.kt`
- **Add Loading State**: Add `var isLoading by mutableStateOf(false)` for global/login loading status.
- **Nullable Data States**: Change `resumeItems`, `libraryLatestItems`, `libraryItems`, `currentLibraryItems`, `latestItems`, `seasons`, `episodes` to nullable `List<Map<String, Any>>?` and initialize them to `null`.
- **Add Media Info State**: Add `var currentMediaInfo by mutableStateOf<Map<String, Any>?>(null)` to hold details for `MediaDetailScreen`.
- **Refactor `login`**: Change `login` to a standard function using `viewModelScope.launch` that updates `isLoading` internally and accepts an `onResult` callback.
- **Refactor Data Fetching**: Update all `load*` functions (e.g., `loadLibraryItems`, `loadMediaInfo`) to set their respective state variables to `null` (loading) before fetching data. Implement `loadMediaInfo(id: String)`.

### 2. Refactor `LoginScreen.kt`
- **Use AppModel Loading**: Remove local `isLoading`. Use `appModel.isLoading`.
- **Update Login Call**: Update the button click handler to call the new `appModel.login` signature with callback.

### 3. Refactor `LibraryScreen.kt`
- **Remove Local State**: Remove local `isLoading` and `libraryItems`.
- **Use AppModel State**: Use `appModel.currentLibraryItems` and `appModel.loadLibraryItems`.
- **Loading Check**: Display loading indicator when `appModel.currentLibraryItems == null`.

### 4. Refactor `MediaDetailScreen.kt`
- **Remove Local State**: Remove local `isLoading` and `mediaInfo`.
- **Use AppModel State**: Use `appModel.currentMediaInfo` and call `appModel.loadMediaInfo`.
- **Loading Check**: Display loading indicator when `appModel.currentMediaInfo == null`.

### 5. Verification
- **Build**: Run `./gradlew assembleDebug` to verify no compilation errors.
