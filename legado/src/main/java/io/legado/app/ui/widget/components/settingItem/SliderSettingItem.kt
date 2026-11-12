package io.legado.app.ui.widget.components.settingItem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.SplicedColumnDivider
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.sliderAccessibility
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField

/**
 * exp.12：MD3 分支已去除，只保留 Miuix 实现（原双引擎中的 miuix 分支）。
 * 吸附/格式化/提交逻辑与原双分支共用同一份，行为不变；
 * [color] 仅为兼容既有调用方签名保留。
 */
@Composable
fun SliderSettingItem(
    title: String,
    color: Color? = null,
    value: Float,
    defaultValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    description: String? = null,
    valueLabel: ((Float) -> String)? = null,
    decimal: Boolean = false,
    onValueChange: (Float) -> Unit
) {

    var expanded by remember { mutableStateOf(false) }
    var isInputMode by remember { mutableStateOf(false) }
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    val textFieldState = rememberTextFieldState()

    // 默认整数模式：滑动吸附到整数；decimal = true 时保留 0.1 精度
    fun snap(v: Float): Float =
        if (decimal) (v * 10).roundToInt() / 10f else v.roundToInt().toFloat()

    fun format(v: Float): String =
        if (v % 1f == 0f) v.toInt().toString() else v.toString()

    LaunchedEffect(value) {
        sliderValue = value
    }

    // 拖动过程中让标题下的数值实时跟随滑块，松手后才真正应用
    val displayDescription = when {
        valueLabel != null -> valueLabel(sliderValue)
        sliderValue != value -> format(sliderValue)
        else -> description
    }

    val sliderAccessibilityValue = displayDescription ?: sliderValue.toString()

    LaunchedEffect(isInputMode) {
        if (isInputMode) {
            textFieldState.edit {
                replace(0, length, format(value))
            }
        }
    }

    fun commitValue() {
        if (isInputMode) {
            textFieldState.text.toString().toFloatOrNull()?.let { num ->
                onValueChange(snap(num).coerceIn(valueRange))
            }
        } else if (sliderValue != value) {
            onValueChange(sliderValue)
        }
    }

    SplicedColumnDivider()

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        BasicComponent(
            title = title,
            summary = displayDescription,
            onClick = {
                if (expanded) {
                    commitValue()
                }
                expanded = !expanded
            }
        )

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                AnimatedContent(
                    targetState = isInputMode,
                    label = "input_slider_switch"
                ) { targetInputMode ->
                    if (targetInputMode) {
                        MiuixTextField(
                            state = textFieldState,
                            lineLimits = TextFieldLineLimits.SingleLine,
                            label = stringResource(
                                R.string.input_value_range,
                                valueRange.start.toInt(),
                                valueRange.endInclusive.toInt()
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        MiuixSlider(
                            value = sliderValue,
                            onValueChange = {
                                sliderValue = snap(it)
                            },
                            onValueChangeFinished = {
                                onValueChange(sliderValue.coerceIn(valueRange))
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

                ConfirmDismissButtonsRow(
                    modifier = Modifier.padding(top = 16.dp),
                    onDismiss = { isInputMode = !isInputMode },
                    onConfirm = {
                        onValueChange(defaultValue)
                        textFieldState.edit {
                            replace(0, length, format(defaultValue))
                        }
                    },
                    dismissText = if (isInputMode) {
                        stringResource(R.string.slider)
                    } else {
                        stringResource(R.string.edit)
                    },
                    confirmText = stringResource(R.string.text_default)
                )
            }
        }
    }
}
