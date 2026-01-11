我将对比 Kotlin 和 Flutter 项目，并重构 `PlayerScreen.kt` 以匹配 Flutter 实现的功能和 UI，特别是使用 ExoPlayer 实现字幕功能，并更新菜单和暂停页面。

### 1. 重构字幕实现 (使用 ExoPlayer 简化)

* **移除手动解析**: 从 `PlayerScreen.kt` 中删除 `SubtitleParser` 类、`SubtitleCue` 数据类以及相关的解析方法 (`fetchSubtitleCues`, `parseAss`, `parseSrt` 等)。

* **配置 MediaItem**:

  * 在 `PlayerScreen.kt` 中添加一个监听 `videoUrl` 和 `subtitleTracks` 的 `LaunchedEffect`。

  * 当 `videoUrl` 变化时，构建包含视频 URI 和每个外挂字幕轨道的 `MediaItem.SubtitleConfiguration` 的 `MediaItem`。

  * 字幕 URI 构建格式: `${appModel.serverUrl}/Videos/${mediaId}/${mediaSourceId}/Subtitles/${index}/Stream.${codec}?api_key=${appModel.apiKey}`。

  * 将 `MediaItem` 设置给 `ExoPlayer` 实例。

  * 使用 `trackSelector` 根据 `selectedSubtitleIndex` 选择激活的字幕轨道。

### 2. 实现菜单弹窗 (对齐 Flutter UI)

* **创建** **`PlayerMenu`** **组件**: 用与 Flutter `_showAlert` 结构一致的综合弹窗替换简单的 `AlertDialog`。

* **实现标签页**:

  * **信息**: 显示剧集名称、集数信息、质量 (分辨率、编码、码率) 和简介。

  * **剧集**: (仅针对剧集) 横向列表显示分集缩略图，支持快速切换。

  * **字幕**: 字幕轨道列表 (包含“关闭”选项)。

  * **音轨**: 音频轨道列表。

  * **播放校正**: “关闭”和“服务器转码”选项。

  * **播放模式**: “列表循环”、“单集循环”、“不循环”选项。

* **导航**: 使用适合电视操作的侧边栏或顶部标签栏切换菜单分类。

### 3. 实现暂停/信息覆盖层 (对齐 Flutter UI)

* **创建** **`PlayerOverlay`** **组件**: 当暂停或 `isShowInfo` 为 true 时显示的覆盖层。

* **顶部区域**: 显示详细媒体信息 (标题、SxxExx、大小、串流方式、视频/音频编码与码率)。

  * 移植 Flutter 的辅助函数: `_streamContainerLine`, `_videoMainLine`, `_audioMainLine` 等。

* **底部区域**:

  * **进度条**: 显示缓冲进度和当前播放进度。

  * **时间**: 显示当前位置和总时长。

  * **提示**: “按菜单键或下键显示菜单”&#x20;

* **中间**: 暂停时显示大播放图标。

### 4. 代码结构与辅助函数

* **移植辅助函数**: 添加 Kotlin 版本的 `formatFileSize`, `formatDuration`, `formatMbps` 等。

* **状态管理**: 确保状态变量 (如 `isShowInfo`, `isPlaying`) 正确控制覆盖层的显示。

* **依赖**: 保持 `androidx.tv:tv-material:1.0.1` 和相关 import 不变。

### 5. 验证

* 验证 ExoPlayer 原生渲染的字幕是否正常显示。

* 验证菜单标签页功能是否正常且与 Flutter 布局一致。

* 验证暂停界面是否显示正确的元数据和进度信息。

