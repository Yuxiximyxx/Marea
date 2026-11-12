package io.legado.app.ui.widget.components.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent

/**
 * exp.13：MD3 分支已去除，只保留 Miuix 实现（原双引擎中的 miuix 分支）。
 * [cornerRadius]、[elevation]、[border] 仅为兼容既有调用方签名保留，
 * Miuix 行不使用卡片圆角/阴影/描边（原 material 分支的 GlassCard 底色一并去除，
 * 与 exp.12 设置行家族收敛后的无卡片底口径一致）。
 */
@Composable
fun SettingCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 4.dp,
    elevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BasicComponent(
        modifier = modifier,
        onClick = onClick,
        content = content
    )
}
