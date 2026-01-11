import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.tv.material3.*
import com.xxxx.emby_tv.ui.theme.ThemeColor

@Composable
fun Emby_tvTheme(
    themeColor: ThemeColor,
    content: @Composable () -> Unit
) {
    // 平滑过渡动画
    val animatedPrimary by animateColorAsState(themeColor.primary, tween(500), label = "primary")
    val animatedSecondary by animateColorAsState(themeColor.secondary, tween(500), label = "secondary")

    // --- 核心修改：动态背景逻辑 ---
    // 方案 A：保持深色但带有 5% 的主题色倾向 (2026 流行做法)
    val dynamicBackground = Color(0xFF0F0F0F).compositeOver(animatedPrimary.copy(alpha = 0.05f))
    // 方案 B：Surface 比背景稍微亮一点，用于卡片和容器
    val dynamicSurface = Color(0xFF1A1A1A).compositeOver(animatedPrimary.copy(alpha = 0.08f))

    val colorScheme = darkColorScheme(
        primary = animatedPrimary,
        onPrimary = Color.White,
        
        // 容器颜色建议使用主色的透明变体，这样按钮和选框会非常好看
        primaryContainer = animatedPrimary.copy(alpha = 0.15f),
        onPrimaryContainer = animatedPrimary,
        
        secondary = animatedSecondary,
        onSecondary = Color.White,

        // 绑定动态背景
        background = dynamicBackground,
        surface = dynamicSurface,
        
        // 2026 TV 规范：surfaceVariant 用于未聚焦但选中的条目背景
        surfaceVariant = animatedPrimary.copy(alpha = 0.12f),
        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
        
        onSurface = Color.White,

    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}


// 1. Surface (基础表面)
// 定义：App 最底层的背景颜色。
// 场景：整个播放器页面的大背景。
// TV 端表现：通常是纯黑色或极深灰色（#000000 或 #121212），以确保大屏幕在暗光环境下不刺眼。
// 2. On-Surface (表面内容色)
// 定义：显示在 Surface 之上的文本或图标的颜色。
// 场景：主要的标题文字、正文、未选中的白色图标。
// TV 端表现：通常是纯白或接近纯白（#FFFFFF 或 #E3E3E3）。由于对比度最高，用于最重要的信息。
// 3. Surface-Variant (表面变体/次要表面)
// 定义：在基础 Surface 之上，用来做区分的次要背景色。
// 场景：
// 列表条目的背景。
// 进度条未填充部分的底色。
// 输入框的背景。
// TV 端表现：比 Surface 稍微亮一点的深灰色。在你的代码中，isSelected 时使用了它的 50% 透明度作为背景，这能让选中的条目在视觉上浮现出来。
// 4. On-Surface-Variant (次要内容色)
// 定义：显示在 Surface-Variant 之上的文本或图标颜色，或者在 Surface 上作为低权重的文字颜色。
// 场景：
// 辅助信息：比如视频的分辨率、码率文字、发布日期。
// 占位符：未输入状态下的提示词。
// 装饰性图标：不需引起用户强烈注意的图标。
// TV 端表现：中灰色。它比 On-Surface 暗，用来告诉用户：“这部分信息没那么重要”。
// 总结对比表（以你的 Emby TV 为例）
// 颜色名称	角色	你的 Emby TV 实例	视觉重要程度
// Surface	底板	整个屏幕的黑色背景	无（背景）
// On-Surface	主文字	视频标题（如《阿凡达》）	最高
// Surface-Variant	卡片底色	设置菜单里每一个选项的背景矩形	中
// On-Surface-Variant	次文字	视频的编码格式、字幕的语言标签	低
// 在你的代码逻辑中：
// kotlin
// containerColor = if (isSelected) 
//     TvMaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
//     else Color.Transparent
// 请谨慎使用此类代码。

// 这里的逻辑是：
// 未选中时：背景透明，直接透出底层的 Surface（全黑）。
// 已选中时：使用 surfaceVariant。这能给用户一个视觉反馈：这个条目虽然现在没获得焦点（焦点通常是亮色），但它是当前生效的设置（比如已选中的字幕）。
// 开发建议：在 TV 端，尽量多使用 On-Surface-Variant 来显示次要信息，这样可以防止整个页面到处都是晃眼的纯白色，从而提升大屏阅读的舒适度。