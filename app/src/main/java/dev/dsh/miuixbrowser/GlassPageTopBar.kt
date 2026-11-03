package dev.dsh.miuixbrowser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassTopAppBar

/**
 * 兼容旧调用的玻璃顶栏：委托 [PageGlassTopBar]。
 * 新代码请直接把顶栏放进 Scaffold 的 topBar 槽。
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
        GlassTopAppBar(
            title = title,
            backdrop = backdrop,
            isContentScrolled = isContentScrolled,
            scrollBehavior = scrollBehavior,
            navigationIcon = onBack?.let { back ->
                {
                    GlassBackButton(onClick = back)
                }
            } ?: {},
            actions = actions,
            bottomContent = bottomContent,
        )
    }
}
