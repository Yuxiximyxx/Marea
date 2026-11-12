package io.legado.app.ui.widget.components.progressIndicator

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator as MiuixLinearProgressIndicator

@Composable
fun AppLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    MiuixLinearProgressIndicator(
        modifier = modifier,
        progress = progress
    )
}
