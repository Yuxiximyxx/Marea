package io.legado.app.ui.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider

/**
 * exp.12：MD3 分支已去除，只保留 Miuix 实现（原双引擎中的 miuix 分支）。
 */
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    accessibilityLabel: String? = null,
    accessibilityValue: String? = null,
) {
    val sliderModifier = modifier.sliderAccessibility(
        label = accessibilityLabel,
        value = accessibilityValue,
    )
    MiuixSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = sliderModifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished
    )
}

internal fun Modifier.sliderAccessibility(
    label: String?,
    value: String?,
): Modifier {
    if (label == null && value == null) return this
    return semantics {
        label?.let { contentDescription = it }
        value?.let { stateDescription = it }
    }
}
