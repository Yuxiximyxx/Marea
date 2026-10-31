package dev.dsh.miuixbrowser

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val tasks = Downloader.tasks
    val backdrop = rememberLayerBackdrop()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(MiuixTheme.colorScheme.surface)
                    .navigationBarsPadding(),
            ) {
                if (tasks.isEmpty()) {
                    Text(
                        text = "还没有下载任务。网页里的下载会先弹确认框，再保存到 /sdcard/Download/mafari。",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .padding(top = statusTop + 56.dp)
                            .padding(24.dp),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(top = statusTop + 56.dp, bottom = 24.dp),
                    ) {
                        item {
                            SmallTitle(text = "本应用下载")
                        }
                        items(tasks.size) { index ->
                            val task = tasks[index]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 8.dp),
                                insideMargin = PaddingValues(14.dp),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(enabled = task.state == DownloadState.DONE && task.uri != null) {
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_VIEW).apply {
                                                                setDataAndType(task.uri, task.mime ?: "*/*")
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            },
                                                        )
                                                    }
                                                },
                                        ) {
                                            Text(
                                                text = task.name,
                                                fontSize = 15.sp,
                                                color = MiuixTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                text = when (task.state) {
                                                    DownloadState.DONE -> Downloader.sizeText(task.bytes) + " · 已保存"
                                                    DownloadState.PAUSED -> "已暂停 · ${Downloader.sizeText(task.bytes)}"
                                                    DownloadState.FAILED -> "失败 · ${task.error ?: "未知错误"}"
                                                    DownloadState.RUNNING -> {
                                                        val percent = (Downloader.progressOf(task) * 100).toInt()
                                                        "$percent% · ${Downloader.sizeText(task.bytes)}"
                                                    }
                                                },
                                                fontSize = 12.sp,
                                                color = if (task.state == DownloadState.FAILED) {
                                                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                                                } else {
                                                    MiuixTheme.colorScheme.primary
                                                },
                                            )
                                        }
                                        when (task.state) {
                                            DownloadState.RUNNING -> IconButton(onClick = { Downloader.pause(task.id) }) {
                                                Icon(imageVector = MiuixIcons.Pause, contentDescription = "暂停")
                                            }
                                            DownloadState.PAUSED -> IconButton(onClick = { Downloader.resume(context, task.id) }) {
                                                Icon(imageVector = MiuixIcons.Play, contentDescription = "继续")
                                            }
                                            DownloadState.FAILED -> IconButton(onClick = { Downloader.retry(context, task.id) }) {
                                                Icon(imageVector = MiuixIcons.Refresh, contentDescription = "重试")
                                            }
                                            DownloadState.DONE -> Unit
                                        }
                                        IconButton(onClick = { Downloader.delete(context, task.id) }) {
                                            Icon(imageVector = MiuixIcons.Close, contentDescription = "删除")
                                        }
                                    }
                                    if (task.state == DownloadState.RUNNING || task.state == DownloadState.PAUSED) {
                                        Spacer(Modifier.height(8.dp))
                                        LinearProgressIndicator(
                                            progress = Downloader.progressOf(task),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp + statusTop)
                    .progressiveTopBar(
                        backdrop = backdrop,
                        tint = MiuixTheme.colorScheme.surface.copy(alpha = 0.62f),
                    ),
            ) {
                SmallTopAppBar(
                    title = "下载",
                    color = Color.Transparent,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                        }
                    },
                    defaultWindowInsetsPadding = false,
                    modifier = Modifier.statusBarsPadding(),
                )
            }
        }
    }
}
