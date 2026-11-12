package io.legado.app.ui.widget.components.title

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun AdaptiveTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    MiuixText(
        modifier = modifier,
        text = text,
        style = MiuixTheme.textStyles.subtitle,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
    )
}
