// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.shader.RuntimeShader
import top.yukonga.miuix.kmp.shader.asBrush
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

// Touch highlight, shared by every popup. Uniforms are set right before each draw.
private val highlightShader: RuntimeShader? by lazy {
    if (isRuntimeShaderSupported()) RuntimeShader(HIGHLIGHT_SHADER) else null
}

private const val HIGHLIGHT_SHADER = """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}"""

/**
 * Drag-to-deform press feedback for dialogs, bottom sheets and popup menus.
 *
 * The deformation maths and spring specs follow Kyant0/AndroidLiquidGlass's InteractiveHighlight
 * and its drag-scale layer: press grows the panel, the drag is tanh-damped into a translation, and
 * the panel stretches along the drag direction. A highlight follows the finger.
 *
 * The gesture only watches the [PointerEventPass.Final] pass and never consumes, so taps, buttons,
 * text fields and scrolling keep working. It engages after the touch slop, and not at all for
 * movement a child has already consumed (scroll, sliders, sheet handle).
 */
@Stable
internal class GlassDragDeform(private val scope: CoroutineScope) {

    private val pressSpec = spring<Float>(0.5f, 300f, 0.001f)
    private val positionSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)
    private val press = Animatable(0f, 0.001f)
    private val position = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var start = Offset.Zero

    /** Gesture observer plus the deforming layer. Apply before the surface modifiers. */
    val layer: Modifier = Modifier
        .pointerInput(Unit) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                var engaged = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (change.isConsumed) {
                        if (engaged) break else continue
                    }
                    if (!engaged) {
                        if ((change.position - down.position).getDistance() < slop) continue
                        engaged = true
                        begin(down.position)
                    }
                    scope.launch { position.snapTo(change.position) }
                }
                if (engaged) end()
            }
        }
        .graphicsLayer {
            val progress = press.value
            val offset = position.value - start
            if (progress == 0f && offset == Offset.Zero) return@graphicsLayer
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@graphicsLayer
            // Panels are much larger than buttons, so the grow/stretch distance is larger than
            // Kyant's 4dp to stay visible.
            val grow = GROW_DP.dp.toPx()
            val scale = lerp(1f, 1f + grow / height, progress)

            val maxOffset = size.minDimension
            val initialDerivative = 0.05f
            translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
            translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

            val maxDragScale = grow / height
            val offsetAngle = atan2(offset.y, offset.x)
            scaleX = scale +
                maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                (width / height).coerceAtMost(1f)
            scaleY = scale +
                maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                (height / width).coerceAtMost(1f)
        }

    /** Draws the touch highlight, clipped to [shape]. Call after the panel content is drawn. */
    fun DrawScope.drawHighlight(shape: Shape) {
        val progress = press.value
        if (progress <= 0f) return
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        clipPath(path) {
            val shader = highlightShader
            if (shader != null) {
                drawRect(Color.White.copy(0.08f * progress), blendMode = BlendMode.Plus)
                val p = position.value
                shader.setFloatUniform("size", size.width, size.height)
                shader.setColorUniform("color", Color.White.copy(0.15f * progress))
                shader.setFloatUniform("radius", size.minDimension * 1.5f)
                shader.setFloatUniform("position", p.x.coerceIn(0f, size.width), p.y.coerceIn(0f, size.height))
                drawRect(brush = shader.asBrush(), blendMode = BlendMode.Plus)
            } else {
                drawRect(Color.White.copy(0.25f * progress), blendMode = BlendMode.Plus)
            }
        }
    }

    /** [drawHighlight] as a modifier, drawn above the content it wraps. */
    fun highlight(shape: Shape): Modifier = Modifier.drawWithContent {
        drawContent()
        drawHighlight(shape)
    }

    private fun begin(down: Offset) {
        start = down
        scope.launch {
            launch { press.animateTo(1f, pressSpec) }
            launch { position.snapTo(down) }
        }
    }

    private fun end() {
        scope.launch {
            launch { press.animateTo(0f, pressSpec) }
            launch { position.animateTo(start, positionSpec) }
        }
    }

    private companion object {
        const val GROW_DP = 8f
    }
}

@Composable
internal fun rememberGlassDragDeform(): GlassDragDeform {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassDragDeform(scope) }
}
