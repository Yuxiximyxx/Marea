package io.legado.app.ui.widget.components.navigation

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.domain.model.settings.customColors
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.LocalHazeState
import io.legado.app.ui.theme.regularHazeEffect
import io.legado.app.ui.widget.components.GlassDefaults
import io.legado.app.ui.widget.components.text.AnimatedText
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem

@Composable
fun AppNavigationBar(
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    alwaysShowLabel: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val configuration = LocalAppUiConfiguration.current
    val themeSettings = configuration.theme
    val miuixMode = when {
        !showLabel -> NavigationBarDisplayMode.IconOnly
        alwaysShowLabel -> NavigationBarDisplayMode.IconAndText
        else -> NavigationBarDisplayMode.IconWithSelectedLabel
    }
    val opacity = (themeSettings.bottomBarOpacity.coerceIn(0, 100)) / 100f
    val customSecondaryColor = themeSettings.customColors(LegadoTheme.isDark).secondary
    val hasCustomSecondary = themeSettings.appTheme == "12" &&
        themeSettings.enableDeepPersonalization && customSecondaryColor != 0
    val hazeState = LocalHazeState.current
    val hazeModifier = if (hazeState != null) {
        Modifier.regularHazeEffect(hazeState)
    } else {
        Modifier
    }

    val baseColor =
        if (hasCustomSecondary) {
            Color(customSecondaryColor)
        } else {
            GlassDefaults.glassColor(
                noBlurColor = MiuixTheme.colorScheme.surface,
                blurAlpha = GlassDefaults.TransparentAlpha
            )
        }
    val finalColor = baseColor.copy(alpha = (baseColor.alpha * opacity).coerceIn(0f, 1f))

    MiuixNavigationBar(
        modifier = modifier.then(hazeModifier),
        color = finalColor,
        mode = miuixMode,
        content = content
    )
}

@Composable
fun RowScope.AppNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelString: String,
    iconVector: ImageVector,
    m3Icon: @Composable () -> Unit,
    m3IndicatorColor: Color,
    m3ShowLabel: Boolean,
    m3AlwaysShowLabel: Boolean = true,
    useCustomIcon: Boolean = false,
) {
    val useCustomIconBox =
        useCustomIcon && LocalAppUiConfiguration.current.appShell.useFloatingBottomBar

    if (useCustomIconBox) {
        Box(
            modifier = modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(m3IndicatorColor)
                )
            }
            m3Icon()
        }
    } else if (useCustomIcon) {
        MiuixCustomNavigationBarItem(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            labelString = labelString,
            showLabel = m3ShowLabel && (m3AlwaysShowLabel || selected),
            icon = m3Icon,
        )
    } else {
        MiuixNavigationBarItem(
            selected = selected,
            onClick = onClick,
            icon = iconVector,
            label = labelString,
            modifier = modifier
        )
    }
}

@Composable
private fun RowScope.MiuixCustomNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    labelString: String,
    showLabel: Boolean,
    icon: @Composable () -> Unit,
) {
    val itemColor = MiuixTheme.colorScheme.onSurfaceContainer.let { color ->
        if (selected) color else color.copy(alpha = 0.4f)
    }
    Column(
        modifier = modifier
            .height(64.dp)
            .weight(1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        if (showLabel) {
            AnimatedText(
                text = labelString,
                color = itemColor,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }
}
