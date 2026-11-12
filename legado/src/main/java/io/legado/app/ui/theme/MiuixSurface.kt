package io.legado.app.ui.theme

import androidx.compose.runtime.Composable

/**
 * exp.12：书架页与设置主界面专用表面环境。
 *
 * 这两块界面已去除 MD3 组件、只保留 Miuix 组件，而 Miuix 组件依赖 MiuixTheme
 * 提供的 CompositionLocal（配色/字阶）。全局引擎仍为 material 时 AppTheme 只
 * 提供 MaterialThemeWrapper，Miuix 组件会读到 LocalColors 的默认值（浅色），
 * 深色模式下必然错色。这里在界面根部补一层与 miuix 引擎完全相同的
 * [MiuixThemeWrapper]（applyBackground = false：页面底色仍由宿主层负责，
 * 不改变既有沉浸口径），使这两块界面在任意引擎设置下都按 miuix 引擎的
 * 环境渲染。引擎已是 miuix 时全局已提供同一环境，直接透传不重复嵌套。
 *
 * 主题映射本身（AppTheme 的引擎选择、ThemeComponents 的配色映射）不在本片
 * 改动范围；阅读页等其它界面不经过本函数，保持原引擎行为。
 */
@Composable
fun MiuixSurface(
    content: @Composable () -> Unit
) {
    val themeColors = LocalLegadoThemeColors.current
    if (ThemeResolver.isMiuixEngine(themeColors.composeEngine)) {
        content()
    } else {
        val configuration = LocalAppUiConfiguration.current
        val customFontFamily = rememberCustomFont(configuration.theme.appFontPath)
        MiuixThemeWrapper(
            themeColors = themeColors,
            customFontFamily = customFontFamily,
            applyBackground = false,
            content = content
        )
    }
}
