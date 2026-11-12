package io.legado.app.ui.widget.components.settingItem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.SettingCard
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.basic.Icon

/**
 * exp.13：行核心收敛为 Miuix 实现——卡片走 [SettingCard]（Miuix BasicComponent），
 * 行本体由 foundation Row + Miuix Icon + AppText 组成，去除 MD3 ListItem。
 * [color]、[cornerRadius] 仅为兼容既有调用方签名保留，Miuix 行不使用卡片底色/圆角。
 * 点击/长按/下拉菜单/展开区与语义（role/stateDescription/toggleableState/disabled）
 * 的行为与原实现逐项一致。
 */
@Composable
fun SettingItem(
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    imageVector: ImageVector? = null,
    color: Color? = null,
    cornerRadius: androidx.compose.ui.unit.Dp = 4.dp,
    title: String,
    description: String? = null,
    option: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    dropdownMenu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    semanticRole: Role? = null,
    semanticStateDescription: String? = null,
    semanticToggleState: Boolean? = null,
    expanded: Boolean = false,
    onExpandChange: ((Boolean) -> Unit)? = null,
    expandContent: (@Composable ColumnScope.() -> Unit)? = null
) {

    var showMenu by remember { mutableStateOf(false) }
    val isExpandable = expandContent != null && onExpandChange != null

    SettingCard(
        modifier = modifier
            .fillMaxWidth(),
        cornerRadius = cornerRadius,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        semanticRole?.let { role = it }
                        semanticStateDescription?.let { stateDescription = it }
                        semanticToggleState?.let {
                            toggleableState = if (it) ToggleableState.On else ToggleableState.Off
                        }
                        if (!enabled) disabled()
                    }
                    .combinedClickable(
                        enabled = enabled,
                        role = semanticRole,
                        onClick = {
                            when {
                                dropdownMenu != null -> showMenu = true
                                isExpandable -> onExpandChange.invoke(!expanded)
                                else -> onClick?.invoke()
                            }
                        },
                        onLongClick = {
                            if (dropdownMenu != null) showMenu = true
                            onLongClick?.invoke()
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (painter != null || imageVector != null) {
                    if (painter != null) {
                        Icon(
                            painter = painter,
                            contentDescription = null,
                            tint = LegadoTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (imageVector != null) {
                        Icon(
                            imageVector = imageVector,
                            contentDescription = null,
                            tint = LegadoTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AppText(
                        text = title,
                        style = LegadoTheme.typography.titleMedium
                    )
                    description?.let {
                        AppText(
                            it,
                            style = LegadoTheme.typography.bodySmallEmphasized,
                            color = LegadoTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    option?.let {
                        AppText(
                            it,
                            style = LegadoTheme.typography.labelMediumEmphasized,
                            color = LegadoTheme.colorScheme.primary
                        )
                    }
                }
                Box(contentAlignment = Alignment.Center) {
                    if (isExpandable && trailingContent == null) {
                        val rotation by animateFloatAsState(
                            if (expanded) 180f else 0f,
                            label = "arrow"
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.rotate(rotation)
                        )
                    } else {
                        trailingContent?.invoke()
                    }

                    dropdownMenu?.let { menu ->
                        RoundDropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }) {
                            menu { showMenu = false }
                        }
                    }
                }
            }

            if (isExpandable) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(expandFrom = Alignment.Top),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 8.dp)
                    ) {
                        expandContent.invoke(this)
                    }
                }
            }
        }
    }
}
