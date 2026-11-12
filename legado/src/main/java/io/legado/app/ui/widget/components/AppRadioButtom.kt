package io.legado.app.ui.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.RadioButton as MiuixRadioButton

/**
 * exp.12：MD3 分支已去除，只保留 Miuix 实现（原双引擎中的 miuix 分支）。
 */
@Composable
fun AppRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    MiuixRadioButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    )
}
