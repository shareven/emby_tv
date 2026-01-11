# MediaDetailScreen 重构计划

我们将参照 Flutter 版本重构 `MediaDetailScreen`，主要目标是统一 UI 布局、实现背景图效果、调整列表顺序，并确保页面加载时默认聚焦在播放按钮上。

## 1. UI 布局重构 (UI Layout Refactoring)
*   **背景图 (Backdrop)**:
    *   使用 `Box` 作为根布局。
    *   底层：使用 `AsyncImage` 显示高清背景图 (Backdrop)，并设置 `ContentScale.Crop`。
    *   遮罩层：在背景图之上叠加一个从上到下的黑色渐变遮罩 (Gradient)，模拟 Flutter 中的 `LinearGradient` 效果，确保文字清晰可见。
*   **主内容区 (Scrollable Content)**:
    *   使用 `Column` 配合 `verticalScroll` 替代目前的布局，确保整体可滚动。
    *   **顶部信息区 (Header)**:
        *   **左侧**: 海报图片 (Poster)，固定尺寸 (160dp x 240dp)，圆角处理。
        *   **右侧**: 
            *   标题 (Title)。
            *   **元数据标签 (Meta Pills)**: 使用圆角胶囊样式显示年份、时长、评级、类型等信息。
            *   **播放/继续播放按钮**: 放置在标签下方。
            *   简介 (Overview): 限制行数，溢出显示省略号。
    *   **剧集选集区 (Seasons & Episodes)**: 保持现有逻辑，放置在顶部信息区下方。
    *   **演职员表 (People List)**: 将其放置在剧集列表**下方** (符合用户要求)。
    *   **详细信息框 (Details Box)**: 在页面最底部添加一个半透明背景的容器，显示类型、制作方、首播日期、路径等详细文本信息 (参考 Flutter 的 `_buildMetaRow`)。

## 2. 焦点控制 (Focus Management)
*   **默认聚焦**:
    *   引入 `FocusRequester` 并绑定到“播放/继续播放”按钮。
    *   使用 `LaunchedEffect` 在页面数据加载完成后，强制请求播放按钮的焦点，确保用户进入页面后可以直接按确认键播放。

## 3. 技术实现细节
*   **辅助组件**: 创建 `MetaPill` Composable 用于显示胶囊标签。
*   **背景图获取**: 增加获取 Backdrop 图片 URL 的逻辑 (参考 Flutter 的 `_backdropUrl`)。
*   **数据加载**: 保持现有的 `LaunchedEffect` 加载逻辑，但需确保 UI 在数据准备好后再渲染以避免焦点丢失。

## 执行步骤
1.  修改 `MediaDetailScreen.kt`，引入 `FocusRequester` 和 `Box` 布局结构。
2.  实现背景图和渐变遮罩。
3.  重构顶部信息区域，添加 Meta Pills 和调整播放按钮位置。
4.  调整 `TvLazyRow` (剧集、演员) 的放置顺序。
5.  添加底部的详细信息面板。
6.  添加播放按钮的自动聚焦逻辑。