package io.legado.app.ui.widget.components.settingItem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.SplicedColumnDivider
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField

/**
 * exp.12：MD3 分支已去除，只保留 Miuix 实现（原双引擎中的 miuix 分支）。
 * 展开/输入状态与原双分支共用同一份，行为不变。
 */
@Composable
fun InputSettingItem(
    title: String,
    value: String,
    defaultValue: String? = "",
    description: String? = null,
    onConfirm: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val state = rememberTextFieldState(initialText = value)

    LaunchedEffect(expanded) {
        if (expanded) {
            state.edit {
                replace(0, length, value)
            }
        }
    }

    SplicedColumnDivider()

    Column(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = title,
            summary = value,
            onClick = { expanded = !expanded }
        )

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                MiuixTextField(
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.edit),
                    onKeyboardAction = {
                        onConfirm(state.text.toString())
                        expanded = false
                    }
                )

                ConfirmDismissButtonsRow(
                    modifier = Modifier.padding(top = 16.dp),
                    onDismiss = {
                        state.edit { replace(0, length, defaultValue.toString()) }
                    },
                    onConfirm = {
                        onConfirm(state.text.toString())
                        expanded = false
                    },
                    dismissText = stringResource(R.string.text_default),
                    confirmText = stringResource(R.string.confirm)
                )
            }
        }
    }
}
