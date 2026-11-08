package dev.dsh.miuixbrowser

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
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
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonDefaults
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 彩色（主色 / 危险色）液态玻璃按钮的叠色不透明度：越大越不透。 */
private const val COLORED_TINT_ALPHA = 0.92f

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
    /** 为 true 时按 catalog 的 LiquidButton 写法着色（Hue 混合 + 75% 叠色），用于主色 / 危险色按钮。 */
    coloredTint: Boolean = false,
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
                                lens(12f.dp.toPx(), 24f.dp.toPx(), depthEffect = true, chromaticAberration = true)
                            },
                            layerBlock = {
                                liquidLayer()
                                alpha = surfaceAlpha
                            },
                            onDrawSurface = {
                                if (coloredTint) {
                                    drawRect(tint, blendMode = BlendMode.Hue)
                                    // 彩色按钮（主色 / 危险色）：叠色不透明度由 0.75 提到 0.92，降低通透度，字更清楚
                                    drawRect(tint.copy(alpha = COLORED_TINT_ALPHA))
                                } else {
                                    drawRect(tint.copy(alpha = tintAlpha))
                                }
                            },
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
    coloredTint: Boolean = false,
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
        coloredTint = coloredTint,
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
        tintAlpha = 0.35f,
        coloredTint = primary,
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
        tintAlpha = 0.35f,
        coloredTint = role != LiquidRole.Normal,
        enabled = enabled,
    ) {
        Text(text = text, color = textColor, style = MiuixTheme.textStyles.button)
    }
}

/**
 * 对话框 / sheet 里的按钮，取代 [GlassDialogButton]（参数兼容）。
 * 液态玻璃模式：Kyant 液态玻璃胶囊，采样 [LocalLiquidBackdrop]（对话框 / sheet 位于采样层之外）；
 * 主色 / 危险色用彩色叠底（不透明度高，不透字）。其它模式：原样走 miuix 的 GlassDialogButton。
 */
@Composable
fun AppDialogButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: GlassDialogButtonRole = GlassDialogButtonRole.Normal,
    cornerRadius: Dp = GlassDialogButtonDefaults.CornerRadius,
    minHeight: Dp = GlassDialogButtonDefaults.MinHeight,
    content: @Composable RowScope.() -> Unit,
) {
    // 采样层为 null（模糊关闭 / 网页态）时与对话框面板一致，退回 miuix 的玻璃按钮
    if (LocalBlurMode.current != BLUR_LIQUID || LocalLiquidBackdrop.current == null) {
        GlassDialogButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            role = role,
            cornerRadius = cornerRadius,
            minHeight = minHeight,
            content = content,
        )
        return
    }
    val colors = MiuixTheme.colorScheme
    val tint = when (role) {
        GlassDialogButtonRole.Primary -> colors.primary
        GlassDialogButtonRole.Destructive -> colors.error
        GlassDialogButtonRole.Normal -> colors.surfaceContainer
    }
    val textColor = when (role) {
        GlassDialogButtonRole.Normal -> colors.onSurface
        else -> Color.White
    }
    LiquidGlassSurface(
        onClick = onClick,
        backdrop = LocalLiquidBackdrop.current,
        modifier = modifier.height(minHeight.coerceAtLeast(44.dp)),
        tint = tint,
        tintAlpha = 0.35f,
        coloredTint = role != GlassDialogButtonRole.Normal,
        enabled = enabled,
    ) {
        CompositionLocalProvider(LocalContentColor provides textColor) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/**
 * 文字按钮，取代 miuix [TextButton]（参数兼容）。
 * - [primary] 为 true 时是主色按钮（对应 textButtonColorsPrimary）。
 * - [inPage] 为 true 表示按钮在页面内容里（位于液态玻璃采样层内部，不能再去采样页面层，否则会递归崩溃），
 *   此时用实色底，保留液态形变与按压高光；弹窗 / sheet 里的按钮保持默认 false，用真正的液态玻璃底。
 */
@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    inPage: Boolean = false,
    cornerRadius: Dp = ButtonDefaults.CornerRadius,
    minWidth: Dp = ButtonDefaults.MinWidth,
    minHeight: Dp = ButtonDefaults.MinHeight,
    insideMargin: PaddingValues = ButtonDefaults.InsideMargin,
    textStyle: TextStyle? = null,
) {
    if (LocalBlurMode.current != BLUR_LIQUID || LocalLiquidBackdrop.current == null) {
        TextButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            cornerRadius = cornerRadius,
            minWidth = minWidth,
            minHeight = minHeight,
            colors = if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
            insideMargin = insideMargin,
            textStyle = textStyle,
        )
        return
    }
    val colors = MiuixTheme.colorScheme
    val tint = if (primary) colors.primary else colors.surfaceContainer
    val textColor = if (primary) Color.White else colors.onSurface
    val labelStyle = textStyle ?: MiuixTheme.textStyles.button
    val hPad = insideMargin.horizontalOnly()
    // 外层 Box 由「不可见的同款文字」撑出宽度（LiquidGlassSurface 的各层都是 matchParentSize，自己没有内在尺寸）
    Box(
        modifier = modifier
            .heightIn(min = minHeight.coerceAtLeast(28.dp))
            .widthIn(min = minWidth),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = labelStyle,
            modifier = Modifier.padding(hPad).alpha(0f),
        )
        LiquidGlassSurface(
            onClick = onClick,
            backdrop = if (inPage) null else LocalLiquidBackdrop.current,
            modifier = Modifier.matchParentSize(),
            tint = if (inPage && !primary) colors.surfaceVariant else tint,
            tintAlpha = 0.35f,
            coloredTint = primary,
            enabled = enabled,
        ) {
            Text(text = text, color = textColor, style = labelStyle)
        }
    }
}

private fun PaddingValues.horizontalOnly(): PaddingValues = PaddingValues(
    start = calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    end = calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
)
