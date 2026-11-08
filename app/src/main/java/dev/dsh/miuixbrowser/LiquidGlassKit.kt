package dev.dsh.miuixbrowser

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.basic.ScrollBehavior
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
    /** 玻璃底板是否显示。顶栏按钮传「内容已上滑」：与 miuix GlassTopAppBar 一致，未滚动时只有图标没有圆形背景。 */
    surfaceVisible: Boolean = true,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val animationScope = rememberCoroutineScope()
    val highlight = remember(animationScope) { InteractiveHighlight(animationScope = animationScope) }
    // 与 miuix 顶栏按钮同一条淡入曲线：350ms 线性
    val surfaceAlpha by animateFloatAsState(
        targetValue = if (surfaceVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = LinearEasing),
        label = "liquidSurfaceAlpha",
    )
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
    val showSurface = surfaceAlpha > 0.001f

    Box(
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) {
        // 玻璃底板与图标分层：底板可以单独淡入淡出，图标始终可见；两层用同一个 liquidLayer，形变一致。
        if (showSurface) {
            if (backdrop != null) {
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                blur(2f.dp.toPx())
                                lens(12f.dp.toPx(), 24f.dp.toPx())
                            },
                            layerBlock = {
                                liquidLayer()
                                alpha = surfaceAlpha
                            },
                            onDrawSurface = { drawRect(tint.copy(alpha = tintAlpha)) },
                        ),
                )
            } else {
                // 关闭模糊 / 网页态：实色底
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            liquidLayer()
                            alpha = surfaceAlpha
                        }
                        .clip(CircleShape)
                        .background(tint),
                )
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer(block = liquidLayer)
                .clip(CircleShape)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
                // 底板不可见时不画按下高光，避免出现一块没有底的亮斑
                .then(if (enabled && showSurface) highlight.modifier else Modifier)
                .then(if (enabled) highlight.gestureModifier else Modifier),
            contentAlignment = Alignment.Center,
            content = { content() },
        )
    }
}

/** 顶栏是否已被内容顶起（上滑）：与 GlassTopAppBar 内部判断相同，contentOffset < 0。 */
@Composable
fun rememberTopBarScrolled(scrollBehavior: ScrollBehavior): Boolean {
    val scrolled by remember(scrollBehavior) {
        derivedStateOf { scrollBehavior.state.contentOffset < 0f }
    }
    return scrolled
}

/**
 * 圆形图标按钮（顶栏按钮用 40dp）。液态玻璃模式用 Kyant 液态玻璃；miuix-glass / 关闭模糊时还原成 miuix 玻璃图标按钮。
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = MiuixTheme.colorScheme.surfaceContainer,
    tintAlpha: Float = 0.35f,
    enabled: Boolean = true,
    surfaceVisible: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (LocalBlurMode.current != BLUR_LIQUID) {
        HapticGlassIconButton(
            onClick = { if (enabled) onClick() },
            modifier = modifier.alpha(if (enabled) 1f else 0.4f),
            content = content,
        )
        return
    }
    LiquidGlassSurface(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.size(size),
        tint = tint,
        tintAlpha = tintAlpha,
        enabled = enabled,
        surfaceVisible = surfaceVisible,
        content = content,
    )
}

/** FAB：56dp 圆形。[primary] 为 true 时用主题色着色。液态玻璃模式用 Kyant 液态玻璃，其它模式是 miuix 的 FAB。 */
@Composable
fun LiquidGlassFab(
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (LocalBlurMode.current != BLUR_LIQUID) {
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            containerColor = if (primary) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainer,
            content = content,
        )
        return
    }
    LiquidGlassButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        size = 56.dp,
        tint = if (primary) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainer,
        tintAlpha = if (primary) 0.8f else 0.35f,
        content = content,
    )
}

/** 顶栏返回键。液态玻璃模式用 Kyant 液态玻璃，其它模式是 miuix 玻璃返回键。 */
@Composable
fun LiquidGlassBackButton(backdrop: Backdrop?, surfaceVisible: Boolean, onClick: () -> Unit) {
    if (LocalBlurMode.current != BLUR_LIQUID) {
        GlassBackButton(onClick = onClick)
        return
    }
    LiquidGlassButton(
        onClick = onClick,
        backdrop = backdrop,
        size = 40.dp,
        surfaceVisible = surfaceVisible,
    ) {
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
    if (LocalBlurMode.current != BLUR_LIQUID) {
        GlassDialogButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            role = when (role) {
                LiquidRole.Primary -> GlassDialogButtonRole.Primary
                LiquidRole.Destructive -> GlassDialogButtonRole.Destructive
                LiquidRole.Normal -> GlassDialogButtonRole.Normal
            },
        ) {
            Text(text = text, style = MiuixTheme.textStyles.button)
        }
        return
    }
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
