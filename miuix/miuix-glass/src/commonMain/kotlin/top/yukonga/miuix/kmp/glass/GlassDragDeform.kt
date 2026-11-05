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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Drag-to-deform press feedback for dialogs, bottom sheets and popup menus.
 *
 * The deformation maths and spring specs follow Kyant0/AndroidLiquidGlass's InteractiveHighlight
 * and its drag-scale layer: press grows the panel, the drag is tanh-damped into a translation, and
 * the panel stretches along the drag direction. Panels get no touch highlight; only buttons do.
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
