package dev.dsh.miuixbrowser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
 * 设置等子页面按钮用主题渐变 CanvasBackdrop + lens 折射。
 */
@Composable
fun rememberLiquidBackdrop(dark: Boolean): Backdrop {
    val draw: DrawScope.() -> Unit = remember(dark) {
        val top = if (dark) Color(0xF02B2B31) else Color(0xF0FFFFFF)
        val bottom = if (dark) Color(0xE81A1A20) else Color(0xE8E7EAF0)
        val brush = Brush.verticalGradient(listOf(top, bottom))
        val block: DrawScope.() -> Unit = { drawRect(brush) }
        block
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

/** 设置等页按钮统一液态玻璃底（圆角矩形） */
@Composable
fun Modifier.liquidButton(corner: Dp = 16.dp): Modifier {
    val dark = isAppDarkTheme()
    val liquid = rememberLiquidBackdrop(dark)
    val shape: () -> Shape = { RoundedCornerShape(corner) }
    return this.liquidGlass(
        backdrop = liquid,
        shape = shape,
        refractionHeight = 8.dp,
        refractionAmount = 16.dp,
        blurRadius = 1.5.dp,
        tint = liquidButtonTint(dark),
        highlight = com.kyant.backdrop.highlight.Highlight(
            width = 0.75.dp,
            alpha = if (dark) 0.3f else 0.85f,
        ),
    )
}

/** 圆形图标按钮液态玻璃底 */
@Composable
fun Modifier.liquidIconButton(): Modifier {
    val dark = isAppDarkTheme()
    val liquid = rememberLiquidBackdrop(dark)
    return this.liquidGlass(
        backdrop = liquid,
        shape = { androidx.compose.foundation.shape.CircleShape },
        refractionHeight = 6.dp,
        refractionAmount = 12.dp,
        blurRadius = 1.5.dp,
        tint = liquidButtonTint(dark),
        highlight = com.kyant.backdrop.highlight.Highlight(
            width = 0.75.dp,
            alpha = if (dark) 0.3f else 0.85f,
        ),
    )
}

/** 统一点击震动的液态圆形图标按钮（设置等页顶栏/行内图标用） */
@Composable
fun LiquidIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .liquidIconButton()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) {
                if (enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** 底栏胶囊圆角（与 glassBar corner 一致） */
fun liquidBarShape(corner: Dp): () -> Shape = { RoundedCornerShape(corner) }

/** 主题半透明罩，叠在折射结果上，避免图标对比度不足 */
@Composable
fun liquidButtonTint(dark: Boolean): Color =
    if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
