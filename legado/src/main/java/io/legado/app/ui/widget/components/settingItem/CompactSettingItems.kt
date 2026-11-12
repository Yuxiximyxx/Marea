package io.legado.app.ui.widget.components.settingItem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.ValueStepper
import io.legado.app.ui.widget.components.sliderAccessibility
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider

/**
 * exp.12：本文件四个紧凑设置行组件的 MD3 分支已全部去除，只保留 Miuix 实现
 * （原双引擎中的 miuix 分支）。各函数的 [color]/[cornerRadius] 参数原先只喂
 * material 分支的 SettingItem，Miuix 行不使用，仅为兼容既有调用方签名保留。
 */

@Composable
fun CompactDropdownSettingItem(
    title: String,
    selectedValue: String,
    displayEntries: Array<String>,
    entryValues: Array<String>,
    description: String? = null,
    imageVector: ImageVector? = null,
    color: Color? = LegadoTheme.colorScheme.onSheetContent,
    cornerRadius: androidx.compose.ui.unit.Dp = 8.dp,
    onValueChange: (String) -> Unit
) {
    val selectedIndex = entryValues.indexOf(selectedValue).coerceAtLeast(0)
    val spinnerItems = displayEntries.toList()

    WindowDropdownPreference(
        title = title,
        summary = description,
        items = spinnerItems,
        selectedIndex = selectedIndex,
        startAction = imageVector?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null
                )
            }
        },
        onSelectedIndexChange = { index ->
            onValueChange(entryValues[index])
        }
    )
}

@Composable
fun CompactSliderSettingItem(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    description: String? = null,
    imageVector: ImageVector? = null,
    color: Color? = LegadoTheme.colorScheme.onSheetContent,
    cornerRadius: androidx.compose.ui.unit.Dp = 8.dp,
    onValueChange: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    var displayValue by remember(value) { mutableFloatStateOf(value) }
    val sliderAccessibilityValue = description ?: displayValue.toString()

    Column(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = title,
            summary = description,
            onClick = { expanded = !expanded },
            startAction = imageVector?.let { icon ->
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null
                    )
                }
            },
            endActions = {
                ValueStepper(
                    value = value,
                    displayValue = displayValue,
                    valueRange = valueRange,
                    onValueChange = onValueChange
                )
            }
        )

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                MiuixSlider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                        displayValue = it
                    },
                    onValueChangeFinished = {
                        onValueChange(sliderValue)
                    },
                    valueRange = valueRange,
                    steps = steps,
                    modifier = Modifier
                        .fillMaxWidth()
                        .sliderAccessibility(
                            label = title,
                            value = sliderAccessibilityValue,
                        )
                )
            }
        }
    }

    LaunchedEffect(value) {
        if (!expanded) {
            sliderValue = value
            displayValue = value
        }
    }
}

@Composable
fun CompactSwitchSettingItem(
    title: String,
    checked: Boolean,
    description: String? = null,
    imageVector: ImageVector? = null,
    color: Color? = LegadoTheme.colorScheme.onSheetContent,
    cornerRadius: androidx.compose.ui.unit.Dp = 8.dp,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
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

@Composable
fun CompactClickableSettingItem(
    title: String,
    description: String? = null,
    imageVector: ImageVector? = null,
    color: Color? = LegadoTheme.colorScheme.onSheetContent,
    cornerRadius: androidx.compose.ui.unit.Dp = 8.dp,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    ArrowPreference(
        title = title,
        summary = description,
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
            trailingContent?.invoke()
        },
        onClick = onClick
    )
}
