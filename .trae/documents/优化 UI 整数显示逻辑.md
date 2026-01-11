# 整数格式化优化计划

为了解决 Emby 接口返回的整数字段 (如年份、集号、未播放数) 可能带小数点的问题 (例如 "2023.0" 或 "1.0")，我将使用 `Utils.kt` 中已有的 `toSafeIntString()` 扩展函数对这些字段进行格式化处理。

## 1. 修改 `MediaDetailScreen.kt`
*   **ProductionYear (年份)**: 将 `mediaInfo["ProductionYear"]?.toString()` 替换为 `mediaInfo["ProductionYear"]?.toSafeIntString()`。

## 2. 修改 `BuildItem.kt`
*   **UnplayedItemCount (未播放数)**: 将繁琐的手动转换链 `userData?.get("UnplayedItemCount")?.toString()?.toDouble()?.toInt()?.toString()` 替换为简洁的 `userData?.get("UnplayedItemCount").toSafeIntString()`。
*   **Season/Episode Numbers (季/集号)**: 在构建副标题 (如 "S1:E1") 时，对 `ParentIndexNumber` 和 `IndexNumber` 使用 `toSafeIntString()`，防止显示为 "S1.0:E1.0"。
*   **ProductionYear (年份)**: 同样将 `item["ProductionYear"]?.toString()` 替换为 `toSafeIntString()`。

## 3. 验证
*   编译并运行应用，检查详情页和列表项中的年份、角标数字、季集号是否显示为干净的整数 (如 "2023", "1", "15")。
