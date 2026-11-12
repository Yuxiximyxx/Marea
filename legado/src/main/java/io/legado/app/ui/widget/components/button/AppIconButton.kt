package io.legado.app.ui.widget.components.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.IconButtonDefaults as MiuixIconButtonDefaults

/**
 * exp.12：MD3 分支已去除，只保留 Miuix 实现（原双引擎中的 miuix 分支）。
 * 原 material 分支的 colors/shape 参数（MD3 类型）随分支一并移除；
 * interactionSource 在 Miuix IconButton 上无对应参数，仅为兼容签名保留。
 */
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    holdDownState: Boolean = false,
    miuixBackgroundColor: Color = Color.Unspecified,
    miuixCornerRadius: Dp? = null,
    miuixMinHeight: Dp? = null,
    miuixMinWidth: Dp? = null,
    content: @Composable () -> Unit
) {
    MiuixIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        holdDownState = holdDownState,
        backgroundColor = miuixBackgroundColor,
        cornerRadius = miuixCornerRadius ?: MiuixIconButtonDefaults.CornerRadius,
        minHeight = miuixMinHeight ?: MiuixIconButtonDefaults.MinHeight,
        minWidth = miuixMinWidth ?: MiuixIconButtonDefaults.MinWidth,
        content = content
    )
}
