package io.legado.app.ui.theme

import androidx.compose.runtime.Composable
import dev.dsh.glasskit.LocalMareaBlurMode
import dev.dsh.glasskit.MAREA_BLUR_LIQUID
import dev.dsh.glasskit.MAREA_BLUR_OFF
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.ui.book.read.ReadMenuConfig

/**
 * exp.7：Marea 材质档位（设置 → 外观 → 模糊效果，落盘 mafari_settings/blur_mode）
 * 桥接进 Legado 界面。档位由 glasskit 的 ProvideMareaBlurMode 在各宿主组合根注入，
 * 与 app 侧 AppSettings.blurMode 同值域：0 MIUIX 磨砂 / 1 液态 / 2 关。
 * 语义为总开关：关 = Legado 主要浮层一律回实色；开 = 按档位取材质，
 * Legado 自身的同名模糊设置在这批表面上让位。
 */

/** Marea 档位 → Legado 阅读菜单模糊模式（一一对应：关→无、液态→液态玻璃、磨砂→Haze）。 */
fun mareaBlurModeToReadMenuBlur(mode: Int): Int = when (mode) {
    MAREA_BLUR_OFF -> ReadMenuBlurMode.None
    MAREA_BLUR_LIQUID -> ReadMenuBlurMode.LiquidGlass
    else -> ReadMenuBlurMode.Haze
}

/**
 * 用当前 Marea 档位覆写阅读菜单的模糊相关字段；其余字段（图标、颜色、布局）原样保留。
 * 液态档同时点亮顶/底栏与浮动图标的液态玻璃按钮，磨砂/关档则一律关掉。
 */
@Composable
fun ReadMenuConfig.mareaMaterialOverride(): ReadMenuConfig {
    val mode = LocalMareaBlurMode.current
    val liquid = mode == MAREA_BLUR_LIQUID
    return copy(
        readMenuTopBarBlurMode = mareaBlurModeToReadMenuBlur(mode),
        readMenuBottomBarBlurMode = mareaBlurModeToReadMenuBlur(mode),
        readMenuTopBarLiquidGlassButtons = liquid,
        readMenuBottomBarLiquidGlassButtons = liquid,
        readMenuFloatingIconLiquidGlass = liquid,
    )
}
