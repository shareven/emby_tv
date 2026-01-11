I will perform a comprehensive cleanup of unused and hardcoded strings across the application, specifically addressing your request to include the missing `footer_notice`.

### 1. Resource Updates (`strings.xml`)
- **Add Missing Strings**: Add `no_image` ("No Image" / "暂无图片") to both English and Chinese resource files.
- **Verification**: Ensure `footer_notice` and other keys are present.

### 2. UI Refactoring & Localization
- **`LoginScreen.kt`**:
    - Add the `footer_notice` (Disclaimer & Repo Link) to the bottom of the left column (QR code side), ensuring it's visible but unobtrusive.
- **`TvInputDialog.kt`**:
    - Re-apply the localization fixes for "输入" (Input), "确认" (Confirm), and "取消" (Cancel) which were seemingly overwritten or missed.
- **`MediaDetailScreen.kt`**:
    - Replace the hardcoded `"No Image"` text with `stringResource(R.string.no_image)`.
- **`Common.kt`**:
    - Replace the hardcoded `"No Data"` text with `stringResource(R.string.no_data)`.
    - Add necessary imports for resource handling.

### 3. Verification
- **Build**: Run `./gradlew assembleDebug` to ensure no compilation errors.
- **Review**: Confirm no hardcoded user-facing strings remain in the primary UI components.