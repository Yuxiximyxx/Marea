package io.legado.app.ui.widget.components.alert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.ProvideAppDensity
import io.legado.app.ui.widget.components.button.PrimaryButton
import io.legado.app.ui.widget.components.button.SecondaryButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * exp.12：MD3 AlertDialog 分支已去除，只保留 Miuix 实现（原双引擎中的 miuix 分支，
 * 基于 WindowDialog）。注意原 miuix 分支不渲染 text 的 SelectionContainer，
 * 正文经 WindowDialog 的 summary 呈现，与原 miuix 分支行为一致。
 */
@Composable
fun AppAlertDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String? = null,
    text: String? = null,
    content: (@Composable () -> Unit)? = null,
    confirmText: String = "确定", // 默认文字
    onConfirm: (() -> Unit)? = null,
    /** 确定按钮是否可点：表单类弹窗里输入还不合法时置 false，而不是点了才弹一句提示。 */
    confirmEnabled: Boolean = true,
    dismissText: String = "取消",
    onDismiss: (() -> Unit)? = null,
) {
    WindowDialog(
        show = show,
        modifier = modifier,
        title = title,
        summary = text,
        onDismissRequest = onDismissRequest,
        content = {
            ProvideAppDensity {
                if (content != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        content()
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onDismiss != null) {
                        SecondaryButton(
                            text = dismissText,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onDismiss()
                            }
                        )
                    }

                    if (onConfirm != null) {
                        PrimaryButton(
                            text = confirmText,
                            modifier = Modifier.weight(1f),
                            enabled = confirmEnabled,
                            onClick = {
                                onConfirm()
                            }
                        )
                    }
                }
            }
        }
    )
}

/**
 * 专为 nullable 数据设计的 AppAlertDialog 重载。
 * 当 [data] 不为 null 时显示弹窗；当 [data] 变为 null 时，自动缓存最后一次数据并播放退出动画。
 */
@Composable
fun <T> AppAlertDialog(
    data: T?,
    onDismissRequest: () -> Unit,
    title: String? = null,
    text: String? = null,
    textProvider: @Composable (T.() -> String)? = null,
    confirmText: String = "确定",
    onConfirm: ((T) -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissText: String = "取消",
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: (@Composable (T) -> Unit)? = null
) {
    var cachedData by remember { mutableStateOf(data) }

    if (data != null) {
        cachedData = data
    }

    val currentData = cachedData
    if (currentData != null) {
        val currentText = text ?: textProvider?.invoke(currentData)
        var lastValidText by remember { mutableStateOf(currentText) }

        if (currentText != null) {
            lastValidText = currentText
        }

        AppAlertDialog(
            show = data != null,
            onDismissRequest = onDismissRequest,
            title = title,
            text = currentText ?: lastValidText,
            modifier = modifier,
            confirmText = confirmText,
            onConfirm = onConfirm?.let { { it(currentData) } },
            confirmEnabled = confirmEnabled,
            dismissText = dismissText,
            onDismiss = onDismiss,
            content = content?.let { { it(currentData) } }
        )
    }
}
