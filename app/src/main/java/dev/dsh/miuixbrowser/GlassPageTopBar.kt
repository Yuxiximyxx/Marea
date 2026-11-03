package dev.dsh.miuixbrowser

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.RowScope

/**
 * 统一的玻璃顶栏：可选 progressive blur 背景带 + GlassTopAppBar。
 * 各子页共用，避免重复拼装 blur / bandBrush / scrollBehavior。
 *
 * @param progressiveBlur 是否叠加 progressiveTextureBlur（关闭时用默认 bandBrush）
 * @param scrollBehavior 滚动驱动大标题收起与材质变化
 * @param isContentScrolled 内容是否已离开顶部
 */
@Composable
fun GlassPageTopBar(
    title: String,
    backdrop: Backdrop?,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    progressiveBlur: Boolean = true,
    scrollBehavior: ScrollBehavior? = null,
    isContentScrolled: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    Box(modifier = modifier) {
        if (progressiveBlur && backdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .progressiveTextureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        gradient = ProgressiveBlur.Top.copy(curve = 0.5f),
                        blurRadius = 80f,
                        colors = BlurDefaults.blurColors(
                            blendColors = listOf(
                                BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(alpha = 0.3f)),
                            ),
                        ),
                    ),
            )
        }
        GlassTopAppBar(
            title = title,
            backdrop = backdrop,
            scrollBehavior = scrollBehavior,
            isContentScrolled = isContentScrolled,
            bandBrush = if (progressiveBlur && backdrop != null) {
                SolidColor(Color.Transparent)
            } else {
                GlassTopAppBarDefaults.bandBrush(MiuixTheme.colorScheme.surface)
            },
            navigationIcon = onBack?.let { back ->
                {
                    GlassIconButton(onClick = back) {
                        top.yukonga.miuix.kmp.basic.Icon(
                            imageVector = top.yukonga.miuix.kmp.icon.MiuixIcons.Back,
                            contentDescription = "返回",
                        )
                    }
                }
            } ?: {},
            actions = actions,
            bottomContent = bottomContent,
        )
    }
}
