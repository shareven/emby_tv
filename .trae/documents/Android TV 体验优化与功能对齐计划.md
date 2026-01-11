# 播放器与详情页体验优化计划

本计划旨在解决详情页列表聚焦显示问题，对齐 Android TV 与 Flutter 版本的播放器信息显示，优化播放器菜单交互，并实现长按快进/快退功能。

## 1. 修复 MediaDetailScreen 列表聚焦遮挡

**目标**：解决列表（季、集、演员）在聚焦第一个或最后一个元素时，内容显示不全的问题。

* **实施方案**：

  * 在 `MediaDetailScreen.kt` 中，为所有 `LazyRow`（包括季列表、集列表、演员列表）添加 `contentPadding` 属性。

  * 设置 `contentPadding = PaddingValues(horizontal = 32.dp)`（或根据设计调整数值），确保列表首尾留有足够余量，使聚焦项能完整显示在屏幕内。

## 2. 播放器暂停信息对齐 (Flutter Parity)

**目标**：确保播放器暂停时显示的 Overlay 信息与 Flutter 版本完全一致（包含转码原因、详细编码参数等）。

* **实施方案**：

  * 在 `PlayerScreen.kt` 中移植 Flutter 的信息生成逻辑，新增以下辅助函数：

    * `_streamContainerLine()`: 显示容器格式和总码率。

    * `_streamModeLine()`: 显示播放模式（直接播放/串流/转码）及转码原因。

    * `_videoMainLine()` / `_videoDetailLine()` / `_videoModeLine()`: 显示视频分辨率、编码、Profile、Level、FPS、硬件加速状态等。

    * `_audioMainLine()` / `_audioDetailLine()` / `_audioModeLine()`: 显示音频语言、编码、声道、采样率及音频转码状态。

  * 改造 `PlayerOverlay` 组件，使用上述函数替换现有的简略信息展示，采用与 Flutter 一致的布局结构（顶部标题、中间播放状态、底部详细参数）。

  * 确保 `toSafeIntString()` 应用于所有数字显示。

## 3. 播放器菜单 UI 与交互优化

**目标**：提升菜单可视性，实现 Tab 焦点自动切换内容。

* **实施方案**：

  * **UI 颜色**：修改 `PlayerMenu` 中的 Tab 和列表项样式，选中状态使用 `MaterialTheme.colorScheme.primary` 作为背景，`onPrimary` 作为前景色，确保高亮清晰。

  * **自动切换**：在 `PlayerMenu` 的 `Tab` 组件上添加 `Modifier.onFocusChanged`。

  * **逻辑实现**：当 Tab 获得焦点（`isFocused == true`）时，直接更新 `selectedTab` 状态，使用户无需按下确认键即可预览对应 Tab 的内容。

## 4. 长按快进/快退功能

**目标**：实现按住遥控器左右键时连续快进/快退。

* **实施方案**：

  * 在 `PlayerScreen` 的 `onKeyEvent` 处理逻辑中引入按键状态监听。

  * **逻辑实现**：

    * 监听 `KeyEventType.KeyDown`：如果是左右键，启动一个协程或计时器。如果按键持续按下超过阈值（如 500ms），进入“长按模式”，每隔一定时间（如 200ms）触发一次 Seek 操作（例如每次 +/- 20秒）。

    * 监听 `KeyEventType.KeyUp`：取消计时器和长按模式。

  * 优化 Seek 时的 UI 反馈，确保进度条平滑更新。

## 待办事项 (Todo)

* [ ] 修改 `MediaDetailScreen.kt`，为所有 `LazyRow` 添加 `contentPadding`。

* [ ] 在 `PlayerScreen.kt` 中实现 Flutter 对应的信息构建函数 (`streamMode`, `videoDetail` 等)。

* [ ] 重构 `PlayerOverlay` UI 以展示详细信息。

* [ ] 优化 `PlayerMenu`，实现 Tab 焦点自动切换和 Primary 配色。

* [ ] 实现 `PlayerScreen` 的长按左右键快进/快退逻辑。

