package io.legado.app.ui.widget.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch

/**
 * exp.12：MD3 分支已去除，只保留 Miuix 实现（原双引擎中的 miuix 分支）。
 * 原 material 分支的 IconSwitch（MD3 Switch + 拇指图标）已无调用方，随分支删除；
 * checkedIcon/uncheckedIcon/showIcon 参数仅为兼容既有调用方签名保留，
 * Miuix Switch 不支持拇指图标（与原 miuix 分支行为一致）。
 */
@Composable
fun AdaptiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedIcon: ImageVector = Icons.Filled.Check,
    uncheckedIcon: ImageVector? = null,
    showIcon: Boolean = true,
    includeStateSemantics: Boolean = true
) {
    MiuixSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = if (includeStateSemantics) {
            modifier.semantics {
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            }
        } else {
            modifier
        },
        enabled = enabled
    )
}

@Composable
fun TinySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedIcon: ImageVector = Icons.Filled.Check,
    uncheckedIcon: ImageVector? = null,
    showIcon: Boolean = true,
    includeStateSemantics: Boolean = true
) {
    MiuixSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = if (includeStateSemantics) {
            modifier
                .scale(0.9f)
                .semantics {
                    toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                }
        } else {
            modifier.scale(0.9f)
        },
        enabled = enabled
    )
}
