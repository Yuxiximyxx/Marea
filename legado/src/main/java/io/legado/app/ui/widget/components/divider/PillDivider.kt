package io.legado.app.ui.widget.components.divider

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PillDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 2.dp,
    widthFraction: Float = 0.2f,
    color: Color = LegadoTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
) {
    // exp.12：引擎分支已去除，只保留 Miuix 分支（dividerLine 配色）
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(thickness)
                .background(MiuixTheme.colorScheme.dividerLine)
        )
    }
}
