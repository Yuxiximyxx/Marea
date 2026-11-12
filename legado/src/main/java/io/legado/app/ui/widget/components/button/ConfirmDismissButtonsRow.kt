package io.legado.app.ui.widget.components.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * exp.12：布局收敛为原双引擎中的 miuix 分支（两按钮等宽平分）。
 */
@Composable
fun ConfirmDismissButtonsRow(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String,
    confirmText: String,
    dismissEnabled: Boolean = true,
    confirmEnabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SecondaryButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
            enabled = dismissEnabled,
            text = dismissText
        )
        PrimaryButton(
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            enabled = confirmEnabled,
            text = confirmText
        )
    }
}
