package io.legado.app.ui.widget.components.progressIndicator

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator as MiuixInfiniteProgressIndicator

@Composable
fun AppContainedLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    MiuixInfiniteProgressIndicator(
        modifier = modifier,
    )
}
