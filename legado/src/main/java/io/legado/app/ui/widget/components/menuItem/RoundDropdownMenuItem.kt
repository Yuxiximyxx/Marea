package io.legado.app.ui.widget.components.menuItem

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.AppIcon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/**
 * exp.12：MD3 分支已去除，只保留 Miuix 实现（原双引擎中的 miuix 分支）。
 * 原 miuix 分支借 material3 的 LocalContentColor 给槽位内容着色，收敛后改用
 * Miuix 自己的 LocalContentColor（MiuixTheme 提供同一本地量）；
 * [color]/[contentPadding] 原先只喂 material 分支，Miuix 行不使用，
 * 仅为兼容既有调用方签名保留。
 */
@Composable
fun RoundDropdownMenuItem(
    text: String,
    color: Color = Color.Unspecified,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    interactionSource: MutableInteractionSource? = null,
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val legadoColorScheme = LegadoTheme.colorScheme
    val textColor = if (isSelected) legadoColorScheme.primary else legadoColorScheme.onSurface
    val backgroundColor = legadoColorScheme.surfaceContainer
    val checkColor = if (isSelected) legadoColorScheme.primary else Color.Transparent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .drawBehind { drawRect(backgroundColor) }
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides textColor) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(12.dp))
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                MiuixText(
                    modifier = Modifier.widthIn(max = 200.dp),
                    text = text,
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                )
            }

            if (trailingIcon != null) {
                Spacer(Modifier.width(12.dp))
                trailingIcon()
            } else {
                Spacer(Modifier.width(12.dp))
                AppIcon(
                    imageVector = MiuixIcons.Basic.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = checkColor
                )
            }
        }
    }
}

@Composable
fun MenuItemIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = Color.Unspecified
) {
    AppIcon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.size(18.dp),
        tint = tint
    )
}
