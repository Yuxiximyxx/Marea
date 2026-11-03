package dev.dsh.miuixbrowser

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Refresh
import top.yukonga.miuix.kmp.icon.glass.Play
import top.yukonga.miuix.kmp.icon.glass.Photos
import top.yukonga.miuix.kmp.icon.glass.Pause
import top.yukonga.miuix.kmp.icon.glass.Music
import top.yukonga.miuix.kmp.icon.glass.Folder
import top.yukonga.miuix.kmp.icon.glass.File
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.AppRecording
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll

private enum class FileKind { IMAGE, VIDEO, AUDIO, ARCHIVE, APK, OTHER }

private fun fileKindOf(name: String): FileKind = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif" -> FileKind.IMAGE
    "mp4", "mkv", "webm", "mov", "avi", "3gp", "ts", "m4v", "flv" -> FileKind.VIDEO
    "mp3", "m4a", "flac", "wav", "aac", "ogg", "opus" -> FileKind.AUDIO
    "zip", "rar", "7z", "tar", "gz", "bz2", "xz" -> FileKind.ARCHIVE
    "apk", "apks", "xapk" -> FileKind.APK
    else -> FileKind.OTHER
}

private fun iconOf(kind: FileKind): ImageVector = when (kind) {
    FileKind.IMAGE -> MiuixIcons.Glass.Photos
    FileKind.VIDEO -> MiuixIcons.Glass.Play
    FileKind.AUDIO -> MiuixIcons.Glass.Music
    FileKind.ARCHIVE -> MiuixIcons.Glass.Folder
    FileKind.APK -> MiuixIcons.Glass.AppRecording
    FileKind.OTHER -> MiuixIcons.Glass.File
}

private fun loadThumbnail(context: Context, uri: Uri, video: Boolean): ImageBitmap? = runCatching {
    if (video) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0)?.asImageBitmap()
        } finally {
            runCatching { retriever.release() }
        }
    } else {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
        }
    }
}.getOrNull()

@Composable
private fun FilePreview(task: DownloadTask) {
    val context = LocalContext.current
    val kind = remember(task.name) { fileKindOf(task.name) }
    val preview by produceState<ImageBitmap?>(initialValue = null, key1 = task.uri, key2 = task.state) {
        value = if (task.uri != null &&
            task.state == DownloadState.DONE &&
            (kind == FileKind.IMAGE || kind == FileKind.VIDEO)
        ) {
            withContext(Dispatchers.IO) { loadThumbnail(context, task.uri, kind == FileKind.VIDEO) }
        } else {
            null
        }
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val thumb = preview
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = iconOf(kind),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun DownloadsScreen(onBack: () -> Unit, blurEnabled: Boolean = true) {
    val context = LocalContext.current
    val tasks = Downloader.tasks
    val backdrop = rememberLayerBackdrop()
    val glass = rememberGlassActive(blurEnabled)
    var pendingDelete by remember { mutableStateOf<DownloadTask?>(null) }
    val deleteTask = pendingDelete
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageGlassTopBar(
                title = "下载",
                backdrop = backdrop,
                glass = glass,
                scrollBehavior = scrollBehavior,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        val topInset = innerPadding.calculateTopPadding()
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .background(MiuixTheme.colorScheme.surface)
                    .navigationBarsPadding(),
            ) {
            if (tasks.isEmpty()) {
                Text(
                    text = "还没有下载任务。网页里的下载会先弹确认框，再保存到 /sdcard/Download/mafari。",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = topInset).padding(24.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = topInset, bottom = 24.dp),
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
                                    FilePreview(task)
                                    Spacer(Modifier.width(10.dp))
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
                                            Icon(imageVector = MiuixIcons.Glass.Pause, contentDescription = "暂停")
                                        }
                                        DownloadState.PAUSED -> IconButton(onClick = { Downloader.resume(context, task.id) }) {
                                            Icon(imageVector = MiuixIcons.Glass.Play, contentDescription = "继续")
                                        }
                                        DownloadState.FAILED -> IconButton(onClick = { Downloader.retry(context, task.id) }) {
                                            Icon(imageVector = MiuixIcons.Glass.Refresh, contentDescription = "重试")
                                        }
                                        DownloadState.DONE -> Unit
                                    }
                                    IconButton(onClick = { pendingDelete = task }) {
                                        Icon(imageVector = MiuixIcons.Glass.Close, contentDescription = "删除")
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
        }

        GlassDialog(
            show = deleteTask != null,
            title = "删除文件",
            summary = deleteTask?.let { it.name + "\n\n会同时删除本地文件" },
            backdrop = if (glass) backdrop else null,
            onDismissRequest = { pendingDelete = null },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassDialogButton(
                    onClick = { pendingDelete = null },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "取消", style = MiuixTheme.textStyles.button)
                }
                GlassDialogButton(
                    onClick = {
                        deleteTask?.let { Downloader.delete(context, it.id) }
                        pendingDelete = null
                    },
                    modifier = Modifier.weight(1f),
                    role = GlassDialogButtonRole.Destructive,
                ) {
                    Text(text = "删除", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}
