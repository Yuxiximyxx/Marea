package io.legado.app.ui.book.read.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * 悬浮卡片退场期间的「还要继续 compose」标记：show 撤下后再多留 [exitMillis]。
 * （exp.11：从已删除的 CastSheetCard 中保留下来的通用小工具，阅读弹层宿主仍在用。）
 */
@Composable
fun rememberSheetAlive(show: Boolean, exitMillis: Long = 180L): Boolean {
    var alive by remember { mutableStateOf(show) }
    LaunchedEffect(show) {
        if (show) {
            alive = true
        } else {
            delay(exitMillis)
            alive = false
        }
    }
    return alive
}

/** 退场那 180ms 里宿主会把行号清成 -1：记住最后一次有效值，卡片不能在半路换成空内容。 */
@Composable
fun rememberSheetArg(show: Boolean, value: Int): Int {
    var held by remember { mutableStateOf(value) }
    if (show && value >= 0) held = value
    return held
}
