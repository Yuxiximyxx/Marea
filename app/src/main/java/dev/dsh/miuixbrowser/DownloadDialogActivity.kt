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
 * 新建下载悬浮弹窗（fork.5 引入；fork.7 按 ABDM 结构改成「接收口 + 弹窗」二合一）：
 * 本 Activity exported=true、透明主题、standard 启动模式，由系统按 intent-filter
 * 直接唤起——SEND 分享、VIEW 按 MIME、VIEW 按文件扩展名（pathPattern 家族）三组入口；
 * 普通网页链接仍只进 MainActivity，两个入口靠 filter 形态在系统选择器里并列共存。
 * fork.5/6 的「MainActivity 分诊转弹窗」链（连同 taskAffinity / singleInstance /
 * excludeFromRecents 那套）已整体删除——那正是 ABDM 没用的做法。
 * 背景透出当前 App，关掉直接回原 App，不建标签页。
 */
class DownloadDialogActivity : ComponentActivity() {

    private val urlState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // fork.7 顺手修复：本 Activity 此前从不调用 Diag.init——进程若被系统直接拉进弹窗
        // （主界面不在场），它内部的 Diag.log 会因 file 未初始化被静默丢弃。补上初始化
        // （追加模式 + 进程内只初始化一次，重复调用无副作用）。
        Diag.init(this)
        Diag.log("DownloadDialogActivity.onCreate: action=${intent?.action} type=${intent?.type}")
        // 冷进程直达：先恢复落盘任务再建任务，否则 persistNow 会整表覆盖旧任务
        // fork.6 防御：恢复 / 初始化异常不许把弹窗 Activity 直接带崩（外部看就是「点了没反应」）
        runCatching { Downloader.restore(this) }
        runCatching { LinkDispatch.init(this) }
        urlState.value = resolveUrl(intent)
        Diag.log("DownloadDialogActivity: resolved url=${urlState.value.take(120)}")
        if (urlState.value.isBlank()) {
            Diag.log("DownloadDialogActivity: blank url, finish")
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
            // fork.7 步进日志：弹窗首帧组合完成，证明 Activity 真的活到了能画面的程度
            LaunchedEffect(Unit) { Diag.log("DownloadDialogActivity: first frame composed") }
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
                            onUrlResolved = { resolved ->
                                // 探测回填的重定向后最终地址：与当前不同才换，避免反复重组
                                if (resolved.isNotBlank() && resolved != urlState.value) {
                                    Diag.log("DownloadDialogActivity: finalUrl backfill -> ${resolved.take(100)}")
                                    urlState.value = resolved
                                }
                            },
                            onOpenInBrowser = { target ->
                                // 逃生口：下到的是网页不是文件，交回主界面打开（显式组件，不再绕选择器）
                                Diag.log("DownloadDialogActivity: escape to browser url=${target.take(100)}")
                                runCatching {
                                    startActivity(
                                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target))
                                            .setClass(this@DownloadDialogActivity, MainActivity::class.java)
                                            .putExtra(MainActivity.EXTRA_OPEN_IN_BROWSER, true),
                                    )
                                }
                                finish()
                            },
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
        val resolved = resolveUrl(intent)
        Diag.log("DownloadDialogActivity.onNewIntent: action=${intent.action} resolved=${resolved.take(120)}")
        if (resolved.isNotBlank()) urlState.value = resolved
    }

    /**
     * 取链：内部分发走 [EXTRA_URL]；系统入口——ACTION_SEND 读 EXTRA_TEXT 并抽出
     * 第一个 http(s) 链接（分享文本常带标题等杂质），其余读 intent.data。
     * 入口层不预判是否文件（判定归系统 intent-filter 与弹窗内探测）。
     */
    private fun resolveUrl(intent: Intent?): String {
        if (intent == null) return ""
        intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() }?.let { return it }
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return ""
            return URL_IN_TEXT.find(text)?.value ?: text.trim()
        }
        return intent.data?.toString() ?: ""
    }

    companion object {
        const val EXTRA_URL = "marea_download_url"

        private val URL_IN_TEXT = Regex("""https?://[^\s<>"']+""")

        fun intent(context: Context, url: String): Intent =
            Intent(context, DownloadDialogActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .apply {
                    // 非 Activity 上下文（如标签持有的 appContext，fork.15 的站内拦截）拉起
                    // 必须带 NEW_TASK，否则系统直接抛异常、弹窗出不来
                    if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
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
    onUrlResolved: (String) -> Unit = {},
    onOpenInBrowser: (String) -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    var fileName by remember(url) { mutableStateOf(Downloader.fileNameFrom(url, null)) }
    var nameEdited by remember(url) { mutableStateOf(false) }
    var probing by remember(url) { mutableStateOf(true) }
    var size by remember(url) { mutableStateOf(-1L) }
    var probeMime by remember(url) { mutableStateOf<String?>(null) }
    var resolvedUrl by remember(url) { mutableStateOf(url) }
    var probeTick by remember(url) { mutableIntStateOf(0) }

    LaunchedEffect(url, probeTick) {
        probing = true
        val result = Downloader.probe(url, null, PrivacyMode.cookieFor(url, privacy = false))
        size = result.size
        probeMime = result.mime
        // 重定向后的最终地址回填：文件名与实际下载都以它为准
        if (!result.finalUrl.isNullOrBlank()) {
            resolvedUrl = result.finalUrl
            if (result.finalUrl != url) onUrlResolved(result.finalUrl)
        }
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
        // 逃生口（fork.7）：探测发现下到的其实是网页（text/html）——多半是中转页 / 假直链，
        // 给一条回主界面的路，而不是把网页当文件硬下
        if (!probing && probeMime?.startsWith("text/html") == true) {
            Spacer(Modifier.height(14.dp))
            AppDialogButton(
                onClick = { onOpenInBrowser(resolvedUrl) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "在浏览器打开", style = MiuixTheme.textStyles.button)
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
