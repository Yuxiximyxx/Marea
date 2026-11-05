package dev.dsh.miuixbrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 玻璃按钮的语义色：对应 miuix 的 Normal / Primary / Destructive */
enum class LiquidRole { Normal, Primary, Destructive }

/**
 * Kyant0/AndroidLiquidGlass（io.github.kyant0:backdrop）的液态玻璃表面，写法对齐官方 catalog 的 LiquidButton：
 * 按压放大、拖拽时跟手形变、按下处有高光（见 InteractiveHighlight.kt）。
 *
 * - [backdrop] 必须是 Kyant 库自己的 LayerBackdrop，录制层要包住「页面内容」，本控件必须放在录制层之外。
 * - [backdrop] 为 null（用户关了模糊 / 网页态）时退回不透明的 [tint] 实色底，形变与高光照常。
 * - 形状固定为 CircleShape：宽高相等是圆，宽大于高就是胶囊。
 */
@Composable
fun LiquidGlassSurface(
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    tint: Color = MiuixTheme.colorScheme.surfaceContainer,
    tintAlpha: Float = 0.35f,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val animationScope = rememberCoroutineScope()
    val highlight = remember(animationScope) { InteractiveHighlight(animationScope = animationScope) }
    // 官方写法：按压放大 + 拖拽时按钮跟手形变，全部走 layerBlock
    val liquidLayer: GraphicsLayerScope.() -> Unit = {
        val width = size.width
        val height = size.height
        val progress = highlight.pressProgress
        val scale = lerp(1f, 1f + 4f.dp.toPx() / height, progress)

        val maxOffset = size.minDimension
        val initialDerivative = 0.05f
        val offset = highlight.offset
        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

        val maxDragScale = 4f.dp.toPx() / height
        val offsetAngle = atan2(offset.y, offset.x)
        scaleX = scale +
            maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
            (width / height).fastCoerceAtMost(1f)
        scaleY = scale +
            maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
            (height / width).fastCoerceAtMost(1f)
    }

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(2f.dp.toPx())
                            lens(12f.dp.toPx(), 24f.dp.toPx())
                        },
                        layerBlock = liquidLayer,
                        onDrawSurface = { drawRect(tint.copy(alpha = tintAlpha)) },
                    )
                } else {
                    // 关闭模糊 / 网页态：实色底，同样跟手形变
                    Modifier
                        .graphicsLayer(block = liquidLayer)
                        .clip(CircleShape)
                        .background(tint)
                },
            )
            .clickable(
                interactionSource = null,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .then(if (enabled) highlight.modifier.then(highlight.gestureModifier) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 圆形液态玻璃图标按钮（顶栏按钮用 40dp）。 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = MiuixTheme.colorScheme.surfaceContainer,
    tintAlpha: Float = 0.35f,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) = LiquidGlassSurface(
    onClick = onClick,
    backdrop = backdrop,
    modifier = modifier.size(size),
    tint = tint,
    tintAlpha = tintAlpha,
    enabled = enabled,
    content = content,
)

/** FAB：56dp 圆形。[primary] 为 true 时用主题色着色（原来的主色 FAB），否则是中性玻璃。 */
@Composable
fun LiquidGlassFab(
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    content: @Composable () -> Unit,
) = LiquidGlassButton(
    onClick = onClick,
    backdrop = backdrop,
    modifier = modifier,
    size = 56.dp,
    tint = if (primary) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainer,
    tintAlpha = if (primary) 0.8f else 0.35f,
    content = content,
)

/** 顶栏返回键（液态玻璃版）。 */
@Composable
fun LiquidGlassBackButton(backdrop: Backdrop?, onClick: () -> Unit) {
    LiquidGlassButton(onClick = onClick, backdrop = backdrop, size = 40.dp) {
        Icon(
            imageVector = MiuixIcons.Glass.ChevronBackward,
            contentDescription = "返回",
            modifier = Modifier.size(24.dp),
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}

/**
 * sheet / 对话框底部左右并排的胶囊按钮，替代 GlassDialogButton。
 * 用法与原来一致：放进 Row，加 Modifier.weight(1f)。
 */
@Composable
fun LiquidGlassPillButton(
    text: String,
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    role: LiquidRole = LiquidRole.Normal,
    enabled: Boolean = true,
) {
    val colors = MiuixTheme.colorScheme
    val tint = when (role) {
        LiquidRole.Primary -> colors.primary
        LiquidRole.Destructive -> colors.error
        LiquidRole.Normal -> colors.surfaceContainer
    }
    val textColor = when (role) {
        LiquidRole.Normal -> colors.onSurface
        else -> Color.White
    }
    LiquidGlassSurface(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.height(48.dp),
        tint = tint,
        tintAlpha = if (role == LiquidRole.Normal) 0.35f else 0.8f,
        enabled = enabled,
    ) {
        Text(text = text, color = textColor, style = MiuixTheme.textStyles.button)
    }
}
