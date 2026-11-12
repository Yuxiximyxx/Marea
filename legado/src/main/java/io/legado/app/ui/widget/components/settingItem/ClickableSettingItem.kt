package io.legado.app.ui.widget.components.settingItem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.widget.components.SplicedColumnDivider
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * exp.12：MD3 分支已去除，只保留 Miuix 实现（原双引擎中的 miuix 分支）。
 * 原 material 分支的 option 值与 trailingContent 借 ArrowPreference 的
 * endActions 槽位保留；onLongClick 在 Miuix 行组件上无对应能力，
 * 且全仓没有调用方传入，故签名保留、暂不生效（与原 miuix 分支行为一致）。
 */
@Composable
fun ClickableSettingItem(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    option: String? = null,
    imageVector: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    SplicedColumnDivider()

    ArrowPreference(
        title = title,
        summary = description,
        modifier = modifier,
        insideMargin = BasicComponentDefaults.InsideMargin,
        startAction = imageVector?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null
                )
            }
        },
        endActions = {
            if (option != null) {
                Text(
                    text = option,
                    color = MiuixTheme.colorScheme.primary
                )
            }
            trailingContent?.invoke()
        },
        onClick = onClick
    )
}
