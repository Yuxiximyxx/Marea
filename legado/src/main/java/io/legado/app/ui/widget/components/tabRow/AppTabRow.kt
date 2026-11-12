package io.legado.app.ui.widget.components.tabRow

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour

/**
 * exp.12：MD3 TabRow 分支已去除，只保留 Miuix 实现（原双引擎中的 miuix 分支）。
 * [isScrollable] 在 Miuix 的 TabRowWithContour 上无对应开关，仅为兼容签名保留
 * （与原 miuix 分支行为一致）。
 */
@Composable
fun AppTabRow(
    tabTitles: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isScrollable: Boolean = true
) {
    TabRowWithContour(
        tabs = tabTitles,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        modifier = modifier
            .padding(vertical = 4.dp),
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = Color.Transparent
        )
    )
}
