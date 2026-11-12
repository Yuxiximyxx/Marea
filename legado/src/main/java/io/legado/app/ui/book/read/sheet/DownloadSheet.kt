package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.model.ReadBook
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog

/**
 * exp.13：MD3 AlertDialog/OutlinedTextField/TextButton 换项目已收敛的
 * [AppAlertDialog]（Miuix WindowDialog）+ [AppTextField]（Miuix）。
 * 确认语义不变：起止章节解析失败或起 > 止时不关闭、不下载。
 */
@Composable
fun DownloadSheet(
    onDismissRequest: () -> Unit,
    onDownload: (start: Int, end: Int) -> Unit,
) {
    val book = ReadBook.book
    var startChapter by remember { mutableStateOf(((book?.durChapterIndex ?: 0) + 1).toString()) }
    var endChapter by remember { mutableStateOf((book?.totalChapterNum ?: 0).toString()) }

    AppAlertDialog(
        show = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.offline_cache),
        content = {
            Column {
                AppTextField(
                    value = startChapter,
                    onValueChange = { startChapter = it },
                    label = stringResource(R.string.start_chapter),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(modifier = Modifier.height(12.dp))
                AppTextField(
                    value = endChapter,
                    onValueChange = { endChapter = it },
                    label = stringResource(R.string.end_chapter),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            val start = startChapter.toIntOrNull() ?: return@AppAlertDialog
            val end = endChapter.toIntOrNull() ?: return@AppAlertDialog
            if (start <= end) {
                onDownload(start, end)
                onDismissRequest()
            }
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = onDismissRequest,
    )
}
