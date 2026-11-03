package dev.dsh.miuixbrowser

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.gesture.PredictiveBackHandler

/**
 * 预测性返回：手势驱动当前页侧滑；松手提交时先播完滑出再 [onBack]，
 * 避免手势中途直接卸载造成跳动。父级页面需保持在下层可见（不要在打开子页时关掉父页）。
 */
@Composable
fun Modifier.predictiveBackPage(
    visible: Boolean,
    onBack: () -> Unit,
): Modifier {
    if (!visible) return this

    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    PredictiveBackHandler(
        enabled = true,
        onProgress = { events ->
            events.collect { event ->
                progress.snapTo(event.progress.coerceIn(0f, 1f))
            }
        },
        onCommit = {
            scope.launch {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.95f, stiffness = 700f),
                )
                onBack()
                progress.snapTo(0f)
            }
        },
        onCancel = {
            scope.launch {
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
                )
            }
        },
    )

    val p = progress.value
    return this.graphicsLayer {
        translationX = size.width * p
        scaleX = 1f - 0.03f * p
        scaleY = 1f - 0.03f * p
        transformOrigin = TransformOrigin(0f, 0.5f)
        alpha = 1f - 0.15f * p
    }
}
