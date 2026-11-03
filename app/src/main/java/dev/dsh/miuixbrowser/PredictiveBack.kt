package dev.dsh.miuixbrowser

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.gesture.PredictiveBackHandler

/**
 * 预测性返回页级 Modifier：对齐 miuix-nav demo 的 PredictiveBackHandler。
 * 手势期间按 progress 做 MiuixDefault 同款侧滑（全宽 trailing 滑出）+ 微缩 + 淡出；
 * 松手提交调用 [onBack]，取消则弹回。
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
        onCommit = onBack,
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
        // miuix MiuixDefault 离开顶层：全宽从 trailing 滑出
        translationX = size.width * p
        scaleX = 1f - 0.02f * p
        scaleY = 1f - 0.02f * p
        transformOrigin = TransformOrigin(0f, 0.5f)
        alpha = 1f - 0.12f * p
    }
}
