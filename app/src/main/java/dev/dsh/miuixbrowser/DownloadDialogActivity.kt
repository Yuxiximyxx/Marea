package dev.dsh.miuixbrowser

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Copy
import top.yukonga.miuix.kmp.icon.glass.Refresh
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 新建下载悬浮弹窗（fork.5）：独立的透明 Activity 承载，背景透出当前 App，
 * 关掉直接回原 App，全程不出现浏览器主界面、不建标签页（照 ABDM 的形态）。
 * MainActivity 分诊命中直链下载 / DOWNLOAD 规则送出失败时拉起。
 */
class DownloadDialogActivity : ComponentActivity() {

    private val urlState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 冷进程直达：先恢复落盘任务再建任务，否则 persistNow 会整表覆盖旧任务
        Downloader.restore(this)
        LinkDispatch.init(this)
        urlState.value = intent?.getStringExtra(EXTRA_URL) ?: intent?.data?.toString() ?: ""
        if (urlState.value.isBlank()) {
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val context = LocalContext.current
            val settings = remember { AppSettings(context) }
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.themeMode) {
                1 -> false
                2 -> true
                else -> systemDark
            }
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            val mode = when (settings.themeMode) {
                1 -> ColorSchemeMode.Light
                2 -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            }
            val themeController = remember(mode) { ThemeController(mode) }
            MiuixTheme(controller = themeController) {
                CompositionLocalProvider(LocalBlurMode provides settings.blurMode) {
                    val url = urlState.value
                    if (url.isNotBlank()) {
                        NewDownloadSheet(
                            url = url,
                            onDismiss = { finish() },
                            onConfirm = { fileName, startNow ->
                                val cookie = PrivacyMode.cookieFor(url, privacy = false)
                                if (startNow) {
                                    Downloader.start(
                                        context = this@DownloadDialogActivity,
                                        url = url,
                                        userAgent = null,
                                        fileName = fileName,
                                        mimeType = null,
                                        cookie = cookie,
                                    )
                                    Toast.makeText(this@DownloadDialogActivity, "已开始下载", Toast.LENGTH_SHORT).show()
                                } else {
                                    Downloader.addPaused(
                                        context = this@DownloadDialogActivity,
                                        url = url,
                                        userAgent = null,
                                        fileName = fileName,
                                        mimeType = null,
                                        cookie = cookie,
                                    )
                                    Toast.makeText(this@DownloadDialogActivity, "已添加到下载列表", Toast.LENGTH_SHORT).show()
                                }
                                finish()
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        urlState.value = intent.getStringExtra(EXTRA_URL) ?: intent.data?.toString() ?: urlState.value
    }

    companion object {
        const val EXTRA_URL = "marea_download_url"

        fun intent(context: Context, url: String): Intent =
            Intent(context, DownloadDialogActivity::class.java).putExtra(EXTRA_URL, url)
    }
}

/**
 * 新建下载弹窗本体（可复用组件）：标题「新建下载」、链接行（可复制）、可编辑文件名、
 * 文件大小异步探测（未知可重探）、底部「添加 / 下载」两键。
 * 「添加」= 只建 PAUSED 任务落盘；「下载」= 立即开始；关闭 / 取消 = 不建任务。
 */
@Composable
fun NewDownloadSheet(
    url: String,
    onDismiss: () -> Unit,
    onConfirm: (fileName: String, startNow: Boolean) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var fileName by remember(url) { mutableStateOf(Downloader.fileNameFrom(url, null)) }
    var nameEdited by remember(url) { mutableStateOf(false) }
    var probing by remember(url) { mutableStateOf(true) }
    var size by remember(url) { mutableStateOf(-1L) }
    var probeTick by remember(url) { mutableIntStateOf(0) }

    LaunchedEffect(url, probeTick) {
        probing = true
        val result = Downloader.probe(url, null, PrivacyMode.cookieFor(url, privacy = false))
        size = result.size
        // 探测带回的 Content-Disposition 文件名只在用户还没手改时覆盖
        if (!nameEdited && !result.fileName.isNullOrBlank()) fileName = result.fileName
        probing = false
    }

    AppBottomSheet(
        onDismissRequest = onDismiss,
        show = true,
        backdrop = null,
        title = "新建下载",
    ) {
        // 链接行：完整 URL（最多三行省略）+ 复制按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = url,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable { clipboard.setText(AnnotatedString(url)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Glass.Copy,
                    contentDescription = "复制链接",
                    modifier = Modifier.size(19.dp),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TextField(
            value = fileName,
            onValueChange = {
                fileName = it
                nameEdited = true
            },
            label = "文件名",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        // 信息行：文件大小（探测中 / 数值 / 未知 + 重探）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "文件大小", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    probing -> "探测中…"
                    size >= 0 -> formatBytes(size)
                    else -> "未知"
                },
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable(enabled = !probing) { probeTick++ },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Glass.Refresh,
                    contentDescription = "重新探测",
                    modifier = Modifier.size(17.dp),
                    tint = if (probing) {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    } else {
                        MiuixTheme.colorScheme.primary
                    },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            AppDialogButton(
                onClick = {
                    onConfirm(fileName.ifBlank { Downloader.fileNameFrom(url, null) }, false)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "添加", style = MiuixTheme.textStyles.button)
            }
            Spacer(Modifier.width(12.dp))
            AppDialogButton(
                onClick = {
                    onConfirm(fileName.ifBlank { Downloader.fileNameFrom(url, null) }, true)
                },
                modifier = Modifier.weight(1f),
                role = GlassDialogButtonRole.Primary,
            ) {
                Text(text = "下载", style = MiuixTheme.textStyles.button)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
}
