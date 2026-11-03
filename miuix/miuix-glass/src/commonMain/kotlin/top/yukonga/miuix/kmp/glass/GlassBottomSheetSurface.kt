// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.glass.internal.ScrimBackdrop
import top.yukonga.miuix.kmp.layout.BottomSheetSurfaceScope
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Default values for [GlassBottomSheet] and [glassBottomSheetSurface]. */
object GlassBottomSheetDefaults {
    val ScrimColor: Color = GlassDialogDefaults.ScrimColor

    /** Bottom-sheet material for the current theme. */
    @Composable
    fun material(): GlassMaterial = GlassMaterials.dialogGlass(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
    )

    /** Bottom-sheet bloom stroke for the current theme. */
    @Composable
    fun stroke(): GlassStroke = GlassStrokes.forTheme(
        isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f,
        light = GlassStrokes.MiddleLight,
        dark = GlassStrokes.MiddleDark,
    )

    /** Opaque fallback when no scene or runtime shader support is available. */
    @Composable
    fun containerColor(): Color = MiuixTheme.colorScheme.background
}

/**
 * Progressive blur band along the top of a glass bottom sheet.
 *
 * The band samples the same scrimmed backdrop as the glass body, blurs it from [blurRadius] at
 * the sheet's top edge down to zero at [height], and cross-fades into the glass body, so no seam
 * is visible where the band ends.
 *
 * @param height Band height measured from the sheet's top edge.
 * @param blurRadius Blur radius in dp at the top edge.
 * @param curve Falloff exponent, see [ProgressiveBlur.curve].
 * @param tint Colour layered over the blurred band. [Color.Unspecified] draws the blur only.
 */
@Immutable
class GlassSheetTopBlur(
    val height: Dp = 96.dp,
    val blurRadius: Float = 80f,
    val curve: Float = 0.5f,
    val tint: Color = Color.Unspecified,
)

/** Covers the top [bandHeight] of the surface with rounded top corners. */
private class SheetTopBandShape(
    private val bandHeight: Dp,
    private val topCornerRadius: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val band = with(density) { bandHeight.toPx() }.coerceAtMost(size.height)
        val r = with(density) { topCornerRadius.toPx() }
            .coerceAtMost(size.width / 2f)
            .coerceAtMost(band / 2f)
        return Outline.Generic(
            Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(0f, 0f, size.width, band),
                        topLeft = CornerRadius(r),
                        topRight = CornerRadius(r),
                        bottomRight = CornerRadius(0f),
                        bottomLeft = CornerRadius(0f),
                    ),
                )
            },
        )
    }
}

/**
 * A glass surface for OverlayBottomSheet's `surface` slot, with rounded top corners.
 *
 * The backdrop is dimmed before blur and sampled in the sheet's translated screen coordinates.
 * Upward overscroll extends the material without a stroke. Content is not refracted, and no
 * additional drop shadow is drawn.
 *
 * @param backdrop Page recording including bars, excluding the sheet host and its scrim.
 *   Null or unavailable runtime shaders use the opaque [containerColor] fallback.
 * @param material Shared dialog/sheet blur and ordered blend layers.
 * @param stroke Optional bloom stroke, omitted on the overscroll extension.
 * @param containerColor Opaque fallback fill.
 * @param topBlur Optional progressive blur band along the top edge. It is skipped on the
 *   overscroll extension and whenever glass sampling is unavailable.
 */
@Composable
fun glassBottomSheetSurface(
    backdrop: Backdrop?,
    material: GlassMaterial = GlassBottomSheetDefaults.material(),
    stroke: GlassStroke? = GlassBottomSheetDefaults.stroke(),
    containerColor: Color = GlassBottomSheetDefaults.containerColor(),
    topBlur: GlassSheetTopBlur? = null,
): @Composable BottomSheetSurfaceScope.() -> Modifier = {
    val effectiveBackdrop = if (backdrop != null && isRuntimeShaderSupported()) {
        remember(backdrop, scrimColor) { ScrimBackdrop(backdrop, scrimColor) }
    } else {
        null
    }
    val shape = remember(cornerRadius) {
        GlassShape(topStart = cornerRadius, topEnd = cornerRadius, bottomEnd = 0.dp, bottomStart = 0.dp)
    }
    val base = Modifier.glassPanel(
        backdrop = effectiveBackdrop,
        shape = shape,
        material = material,
        stroke = if (isOverscroll) null else stroke,
        shadow = null,
        shading = false,
        fallback = Modifier.clip(shape).background(containerColor),
    )
    if (topBlur != null && effectiveBackdrop != null && !isOverscroll) {
        val bandPx = with(LocalDensity.current) { topBlur.height.toPx() }
        // 渐变终点按顶部带高度占 Sheet 总高度的比例计算。
        var surfaceHeight by remember { mutableIntStateOf(0) }
        val endFraction = if (surfaceHeight > 0) (bandPx / surfaceHeight).coerceIn(0.02f, 1f) else 1f
        val bandShape = remember(cornerRadius, topBlur.height) { SheetTopBandShape(topBlur.height, cornerRadius) }
        val colors = BlurDefaults.blurColors(
            blendColors = if (topBlur.tint == Color.Unspecified) {
                emptyList()
            } else {
                listOf(BlendColorEntry(color = topBlur.tint))
            },
        )
        base
            .onSizeChanged { surfaceHeight = it.height }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .progressiveTextureBlur(
                backdrop = effectiveBackdrop,
                shape = bandShape,
                gradient = ProgressiveBlur(90f, 0f, endFraction, topBlur.curve),
                blurRadius = topBlur.blurRadius,
                colors = colors,
            )
    } else {
        base
    }
}
