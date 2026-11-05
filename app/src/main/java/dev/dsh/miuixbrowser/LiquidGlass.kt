package dev.dsh.miuixbrowser

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * 液态玻璃公共底（AndroidLiquidGlass / `io.github.kyant0:backdrop`）。
 *
 * WebView 无法进 Compose layerBackdrop 采样，浏览页底栏/按钮用主题渐变
 * CanvasBackdrop + 折射（lens）得到液态玻璃质感，而不是空白。
 */
@Composable
fun rememberLiquidBackdrop(dark: Boolean): Backdrop {
    val draw: DrawScope.() -> Unit = remember(dark) {
        val top = if (dark) Color(0xF02B2B31) else Color(0xF0FFFFFF)
        val bottom = if (dark) Color(0xE81A1A20) else Color(0xE8E7EAF0)
        val brush = Brush.verticalGradient(listOf(top, bottom))
        { drawRect(brush) }
    }
    return rememberCanvasBackdrop(draw)
}

/**
 * 液态玻璃 Modifier：vibrancy + blur + lens 折射。
 * [shape] 须为 CornerBasedShape（圆角矩形 / 圆）才能走 lens。
 */
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: () -> Shape,
    refractionHeight: Dp = 10.dp,
    refractionAmount: Dp = 20.dp,
    blurRadius: Dp = 2.dp,
    tint: Color = Color.Unspecified,
    highlight: Highlight = Highlight.Default,
    shadow: Shadow? = null,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = shape,
    effects = {
        vibrancy()
        blur(blurRadius.toPx())
        lens(refractionHeight.toPx(), refractionAmount.toPx())
    },
    highlight = { highlight },
    shadow = { shadow },
    onDrawSurface = {
        if (tint != Color.Unspecified) {
            drawRect(tint)
        }
    },
)

/** 底栏胶囊圆角（与 glassBar corner 一致） */
fun liquidBarShape(corner: Dp): () -> Shape = { RoundedCornerShape(corner) }

/** 圆形按钮玻璃形状 */
val liquidCircleShape: () -> Shape = { androidx.compose.foundation.shape.CircleShape }

/** 主题半透明罩，叠在折射结果上，避免图标对比度不足 */
@Composable
fun liquidButtonTint(dark: Boolean): Color =
    if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
