# MediaDetailScreen 重构计划

根据您的要求，我将按照 Flutter 版本的布局和交互逻辑对 `MediaDetailScreen` 进行重构。

## 1. UI 布局与背景重构
*   **根布局**: 使用 `Box` 作为根容器，实现图层叠加效果。
*   **背景层 (Backdrop)**:
    *   使用 `AsyncImage` 加载高清背景图 (Backdrop)，填充整个屏幕 (`fillMaxSize`)。
    *   设置 `ContentScale.Crop` 保证图片铺满。
*   **遮罩层 (Gradient)**:
    *   在背景图之上叠加一层黑色垂直渐变 (`Brush.verticalGradient`)。
    *   调整透明度 (Alpha) 从顶部到底部逐渐加深，确保前景文字内容清晰可读。

## 2. 内容结构与排序
将页面内容包裹在 `Column` 中，并按以下顺序排列：
1.  **顶部信息区 (Header)**:
    *   左侧：电影/剧集海报 (`Card` + `AsyncImage`)。
    *   右侧：标题、元数据标签 (年份、时长、评分等)、**播放/继续播放按钮**、剧情简介 (Overview)。
2.  **选集列表 (Seasons & Episodes)**:
    *   如果是剧集类型，显示季列表和集列表。
    *   位于顶部信息区正下方。
3.  **演职员表 (People/Cast)**:
    *   **调整位置**: 将演职员表放置在选集列表的**下方** (符合 Flutter 版布局)。
    *   使用 `TvLazyRow` 横向展示演员头像和姓名。
4.  **详细信息 (Details Box)**:
    *   页面最底部显示详细元数据 (类型、工作室、首映日期、路径等)。

## 3. 默认焦点逻辑
*   **焦点控制器**: 创建 `playButtonFocusRequester` 并绑定到“播放/继续播放”按钮。
*   **自动聚焦**:
    *   使用 `LaunchedEffect` 监听 `mediaInfo` 和数据加载状态。
    *   当页面数据加载完毕 (Series 数据加载完成或 Movie 信息就绪) 且 UI 渲染后，自动请求焦点到播放按钮。
    *   添加微小延时 (100ms) 确保组件已挂载，避免焦点丢失。

## 4. 技术实现点
*   使用 `androidx.tv.material3` 组件库构建 TV 专属 UI。
*   保持与 Flutter 版本一致的参数传递和页面跳转逻辑。
