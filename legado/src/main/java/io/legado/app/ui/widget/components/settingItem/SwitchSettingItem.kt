package io.legado.app.ui.widget.components.settingItem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.widget.components.SplicedColumnDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.SwitchPreference


/**
 * exp.12：MD3 分支已去除，只保留 Miuix 实现（原双引擎中的 miuix 分支）。
 * [color] 仅为兼容既有调用方签名保留，Miuix 行不使用卡片底色。
 */
@Composable
fun SwitchSettingItem(
    title: String,
    description: String? = null,
    checked: Boolean,
    imageVector: ImageVector? = null,
    color: Color? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    SplicedColumnDivider()

    SwitchPreference(
        title = title,
        summary = description,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier,
        enabled = enabled,
        startAction = imageVector?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null
                )
            }
        }
    )
}
