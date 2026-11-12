package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarScrollBehavior
import io.legado.app.ui.widget.components.topbar.MiuixGlassScrollBehavior
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState as miuixRememberPullToRefreshState

/**
 * exp.12：MD3 PullToRefreshBox 分支已去除，只保留 Miuix 实现
 * （原双引擎中的 miuix 分支）。[isRefreshing] 在 Miuix 分支里原本就不驱动
 * 指示器（指示器靠本地短暂状态），签名保留兼容调用方。
 */
@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    topPadding: Dp = 0.dp,
    scrollBehavior: GlassTopAppBarScrollBehavior? = null,
    content: @Composable () -> Unit,
) {
    // Miuix 的 PullToRefresh 没有 enabled 参数，无法内部关闭手势，
    // 因此只能在 enabled=false 时整体退化成普通容器。
    if (enabled) {
        // Let the miuix library handle gesture/animation, but skip the persistent
        // Refreshing state that blocks all scroll events.
        // localIsRefreshing is true only briefly so the indicator shows then hides,
        // while the actual refresh runs independently.
        var localIsRefreshing by remember { mutableStateOf(false) }

        LaunchedEffect(localIsRefreshing) {
            if (localIsRefreshing) {
                delay(300)
                localIsRefreshing = false
            }
        }

        MiuixPullToRefresh(
            isRefreshing = localIsRefreshing,
            onRefresh = {
                localIsRefreshing = true
                onRefresh()
            },
            modifier = modifier,
            contentPadding = PaddingValues(top = topPadding + 16.dp),
            pullToRefreshState = miuixRememberPullToRefreshState(),
            topAppBarScrollBehavior = (scrollBehavior as? MiuixGlassScrollBehavior)?.miuixBehavior,
            refreshTexts = listOf(
                stringResource(R.string.pull_to_refresh),
                stringResource(R.string.release_to_refresh),
                stringResource(R.string.refreshing),
                stringResource(R.string.refreshing)
            )
        ) {
            content()
        }
    } else {
        Box(modifier) {
            content()
        }
    }
}
