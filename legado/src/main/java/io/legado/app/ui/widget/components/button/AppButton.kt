package io.legado.app.ui.widget.components.button

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.legado.app.ui.theme.LegadoTheme
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/**
 * exp.12：MD3 分支已去除，只保留 Miuix 实现（原双引擎中的 miuix 分支）。
 */
@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String
) {
    MiuixButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = MiuixButtonDefaults.buttonColorsPrimary()
    ) {
        MiuixText(text = text, style = LegadoTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String
) {
    MiuixButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = MiuixButtonDefaults.buttonColors()
    ) {
        MiuixText(text = text, style = LegadoTheme.typography.labelLarge)
    }
}
