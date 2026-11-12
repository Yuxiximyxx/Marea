package io.legado.app.ui.widget.components.checkBox

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox

/**
 * exp.12：MD3 分支已去除，只保留 Miuix 实现（原双引擎中的 miuix 分支）。
 */
@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    includeStateSemantics: Boolean = true
) {
    val state = if (checked) ToggleableState.On else ToggleableState.Off
    val semanticModifier = if (includeStateSemantics) {
        modifier.semantics {
            toggleableState = state
        }
    } else {
        modifier
    }

    MiuixCheckbox(
        state = state,
        onClick = onCheckedChange?.let { { it(!checked) } },
        modifier = semanticModifier,
        enabled = enabled
    )
}

@Composable
fun AppTriStateCheckbox(
    state: ToggleableState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    MiuixCheckbox(
        state = state,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    )
}
