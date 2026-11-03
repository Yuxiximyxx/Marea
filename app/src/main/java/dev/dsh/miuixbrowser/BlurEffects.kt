package dev.dsh.miuixbrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.noiseDither
import top.yukonga.miuix.kmp.blur.progressiveBlur
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.blur.textureBlurEffect
import top.yukonga.miuix.kmp.blur.highlight.Highlight

/**
 * 仅覆盖表面顶部 [bandHeight] 的圆角矩形（上角用 [topCornerRadius]）。
 * 用于把 progressiveTopBar 限制在 sheet 标题区，而不是整页。
 */
class TopBandShape(
    private val bandHeight: Dp,
    private val topCornerRadius: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val band = with(density) { bandHeight.toPx() }.coerceAtMost(size.height)
        val r = with(density) { topCornerRadius.toPx() }
            .coerceAtMost(size.width / 2f)
            .coerceAtMost(band / 2f)
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, size.width, band),
                    topLeft = CornerRadius(r),
                    topRight = CornerRadius(r),
                    bottomRight = CornerRadius(0f),
                    bottomLeft = CornerRadius(0f),
                ),
            )
        }
        return Outline.Generic(path)
    }
}

/**
 * 顶栏/标题区渐进模糊：MIUIX progressiveBlur，方向自上而下。
 * [shape] 可限制绘制区域（如 sheet 顶部 band）。
 */
@Composable
fun Modifier.progressiveTopBar(
    backdrop: Backdrop,
    shape: Shape = RectangleShape,
    tint: Color = Color.Unspecified,
    radius: Float = 30f,
    startFraction: Float = 0f,
    endFraction: Float = 1f,
    curve: Float = 0.68f,
    noise: Float = 0f,
    highlight: Highlight? = null,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return background(if (tint == Color.Unspecified) Color.Transparent else tint.copy(alpha = 1f), shape)
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                progressiveBlur(
                    radius.dp.toPx(),
                    radius.dp.toPx(),
                    ProgressiveBlur(90f, startFraction, endFraction, curve),
                )
                noiseDither(noise)
            },
            highlight = { highlight },
        )
        .background(if (tint == Color.Unspecified) Color.Transparent else tint, shape)
}

/** 静态表面的纹理模糊（主页卡片等，不含 WebView） */
@Composable
fun Modifier.textureBlurBar(
    backdrop: Backdrop,
    shape: Shape = RectangleShape,
    tint: Color = Color.Unspecified,
    radius: Float = 22f,
    noise: Float = 0.06f,
    highlight: Highlight? = null,
): Modifier = this
    .drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            textureBlurEffect(
                blurRadiusX = radius.dp.toPx(),
                blurRadiusY = radius.dp.toPx(),
                noiseCoefficient = noise,
            )
        },
        highlight = { highlight },
    )
    .background(if (tint == Color.Unspecified) Color.Transparent else tint, shape)


/**
 * 页面顶部的 miuix 渐进模糊带（自上而下由强到弱），官方 glass demo 的同款写法。
 *
 * - 必须放在 [matchParentSize][androidx.compose.foundation.layout.BoxScope.matchParentSize] 的位置：
 *   本身只画顶部 `(状态栏 + [barHeight] + 20dp) * 1.2` 的高度，不撑大父布局，也不拦截触摸。
 * - [backdrop] 必须是录制「页面内容」的层，并且本组件要放在录制层之外，否则会采样到自己。
 * - [tint] 叠在模糊之上的主题色雾化（默认跟随当前主题的 surface，深色模式下是深色而不是白色）；
 *   传 [Color.Unspecified] 表示不叠色，只做纯模糊（例如首页壁纸之上）。
 * - [alpha] 在绘制阶段读取，用来跟随滚动淡入淡出。
 */
@Composable
fun TopProgressiveBlurBand(
    backdrop: Backdrop,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
    barHeight: Dp = TopAppBarDefaults.CollapsedHeight,
    tint: Color = MiuixTheme.colorScheme.surface.copy(alpha = 0.3f),
    blurRadius: Float = 80f,
) {
    val statusInsets = WindowInsets.statusBars
    val colors = BlurDefaults.blurColors(
        blendColors = if (tint == Color.Unspecified) emptyList() else listOf(BlendColorEntry(color = tint)),
    )
    Box(
        modifier = modifier
            .layout { measurable, constraints ->
                val barPx = statusInsets.getTop(this) + barHeight.toPx()
                val blurHeight = ((barPx + 20.dp.toPx()) * 1.2f).roundToInt()
                val placeable = measurable.measure(
                    constraints.copy(minHeight = blurHeight, maxHeight = blurHeight),
                )
                // 向下溢出，不改变父布局（Scaffold topBar）测得的高度。
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.placeRelative(0, 0)
                }
            }
            .graphicsLayer { this.alpha = alpha().coerceIn(0f, 1f) }
            .progressiveTextureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                gradient = ProgressiveBlur(90f, 0f, 1f, 0.5f),
                blurRadius = blurRadius,
                colors = colors,
            ),
    )
}
