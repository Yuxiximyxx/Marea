package dev.dsh.miuixbrowser

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch

/**
 * 预测性返回页级 Modifier：子页展示时消费返回手势，
 * 手势期间按 progress 侧滑 + 微缩 + 淡出，松手提交时调用 [onBack]。
 * 无 NavigationEvent 宿主时退化为普通返回。
 */
@Composable
fun Modifier.predictiveBackPage(
    visible: Boolean,
    onBack: () -> Unit,
): Modifier {
    if (!visible) return this

    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val hasDispatcher = LocalNavigationEventDispatcherOwner.current != null

    if (hasDispatcher) {
        val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(
            state = backState,
            isBackEnabled = true,
            onBackCancelled = {
                scope.launch {
                    progress.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 400f))
                }
            },
            onBackCompleted = onBack,
        )
        LaunchedEffect(backState) {
            snapshotFlow { backState.transitionState }.collect { state ->
                if (
                    state is NavigationEventTransitionState.InProgress &&
                    state.direction == NavigationEventTransitionState.TRANSITIONING_BACK
                ) {
                    progress.snapTo(state.latestEvent.progress.coerceIn(0f, 1f))
                }
            }
        }
    }

    val p = progress.value
    return this.graphicsLayer {
        translationX = size.width * p * 0.35f
        scaleX = 1f - 0.06f * p
        scaleY = 1f - 0.06f * p
        transformOrigin = TransformOrigin(0f, 0.5f)
        alpha = 1f - 0.25f * p
    }
}
