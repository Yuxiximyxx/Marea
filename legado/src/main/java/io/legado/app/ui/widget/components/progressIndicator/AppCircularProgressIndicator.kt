package io.legado.app.ui.widget.components.progressIndicator

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator

@Composable
fun AppCircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    strokeWidth: Dp = 4.dp,
) {
    MiuixCircularProgressIndicator(
        modifier = modifier,
        progress = progress,
        strokeWidth = strokeWidth,
    )
}
