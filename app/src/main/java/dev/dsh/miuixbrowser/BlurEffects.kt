package dev.dsh.miuixbrowser

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.noiseDither
import top.yukonga.miuix.kmp.blur.progressiveBlur
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
