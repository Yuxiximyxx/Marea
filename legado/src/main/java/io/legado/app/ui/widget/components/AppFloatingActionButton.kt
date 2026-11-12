package io.legado.app.ui.widget.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ProvideAppDensity
import io.legado.app.ui.widget.components.text.AppText
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.PlainTooltip
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TooltipDefaults
import top.yukonga.miuix.kmp.basic.rememberTooltipState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.FloatingActionButton as MiuixFloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun AppFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tooltipText: String? = null,
    icon: ImageVector? = null,
    containerColor: Color = LegadoTheme.colorScheme.primaryContainer,
    contentColor: Color = LegadoTheme.colorScheme.primary,
    content: (@Composable () -> Unit)? = null
) {
    val fabContent: @Composable () -> Unit = {
        if (icon != null) {
            MiuixIcon(
                imageVector = icon,
                contentDescription = tooltipText,
                tint = containerColor
            )
        } else {
            content?.invoke()
        }
    }

    if (tooltipText != null) {
        Box(modifier = modifier) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above
                ),
                tooltip = {
                    ProvideAppDensity {
                        PlainTooltip(
                            containerColor = LegadoTheme.colorScheme.surfaceContainerLow,
                            contentColor = LegadoTheme.colorScheme.onSurface,
                        ) { AppText(tooltipText) }
                    }
                },
                state = rememberTooltipState(),
            ) {
                MiuixFloatingActionButton(
                    onClick = onClick,
                    content = fabContent,
                    containerColor = LegadoTheme.colorScheme.surfaceContainer
                )
            }
        }
    } else {
        MiuixFloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            content = fabContent,
            containerColor = LegadoTheme.colorScheme.surfaceContainer
        )
    }
}

data class FabMenuItem(
    val icon: ImageVector,
    val label: String,
    val action: () -> Unit
)

@Composable
fun AppFloatingActionButtonMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<FabMenuItem>,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEachIndexed { index, (icon, label, action) ->
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(delayMillis = index * 40)) +
                        scaleIn(tween(delayMillis = index * 40), initialScale = 0.8f),
                exit = fadeOut(tween(80)) + scaleOut(tween(80), targetScale = 0.8f)
            ) {
                Button(
                    onClick = {
                        action()
                        onExpandedChange(false)
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiuixIcon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MiuixText(
                            text = label,
                            style = MiuixTheme.textStyles.subtitle
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f)
        ) {
            MiuixFloatingActionButton(
                onClick = { onExpandedChange(!expanded) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    MiuixIcon(
                        imageVector = if (expanded) Icons.Filled.Close
                        else Icons.AutoMirrored.Filled.MenuOpen,
                        contentDescription = stringResource(R.string.menu),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    MiuixText(stringResource(R.string.menu), color = Color.White)
                }
            }
        }
    }
}
