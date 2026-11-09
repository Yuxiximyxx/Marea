package dev.dsh.miuixbrowser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
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
 * fork.17：Marea 是默认浏览器时系统把直链判给 MainActivity 的那条路，改由
 * MainActivity 当跳板转交（分诊在 setContent 之前、转交后主界面不组合浏览器界面
 * 并退场）。呈现判据看 Diag 的 `dialog window focused`。
 * fork.19：与主界面合回同一任务——去掉 fork.17 的独立 taskAffinity 与拉起恒带
 * NEW_TASK。当时拆任务是怀疑任务归属让弹窗不可见，fork.18 已证明真凶是 miuix
 * 覆盖层 sheet（已换普通内联面板）；同任务后外部点直链只发生一次窗口切换、
 * 无中间黑闪（ABDM 跳板+弹窗本就同任务）。
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
                        DownloadConfirmPanel(
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
                                Diag.log("DownloadDialogActivity: confirm clicked startNow=$startNow fileName=$fileName")
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
                                Diag.log("DownloadDialogActivity: task created via ${if (startNow) "Downloader.start" else "Downloader.addPaused"} fileName=$fileName")
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

    // fork.17 呈现证据：fork.14~16 的日志只证明本 Activity「组成了」，证明不了窗口真到了
    // 用户眼前。onResume 与窗口焦点各留一行带同一判据串的 Diag——真机 diag 里出现
    // `dialog window focused` 才算弹窗真正露面，缺失即窗口没成为可见顶层。
    override fun onResume() {
        super.onResume()
        Diag.log("DownloadDialogActivity: dialog window focused (onResume)")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            Diag.log("DownloadDialogActivity: dialog window focused (onWindowFocusChanged)")
        }
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

        fun intent(context: Context, url: String): Intent {
            val intent = Intent(context, DownloadDialogActivity::class.java)
                .putExtra(EXTRA_URL, url)
            // fork.19：与主界面合回同一任务——从 Activity 拉起就是同任务普通压栈，
            // 不再恒带 NEW_TASK；仅非 Activity 上下文（BrowserState 的 appContext）
            // 系统硬性要求 NEW_TASK 才补（无此标记从应用上下文 startActivity 直接抛异常）。
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return intent
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
    var probeError by remember(url) { mutableStateOf<String?>(null) }
    var resolvedUrl by remember(url) { mutableStateOf(url) }
    var probeTick by remember(url) { mutableIntStateOf(0) }

    LaunchedEffect(url, probeTick) {
        probing = true
        probeError = null
        Diag.log("NewDownloadSheet: probe begin url=${url.take(100)}")
        // fork.16：落定只认 Downloader.probe 的真实返回。fork.15 的
        // runCatching { withTimeoutOrNull(20s) { probe } }.getOrNull() 把「真超时」
        // 与「effect 协程被取消时 runCatching 吞掉的 CancellationException」混成同一个
        // null——真机实证 probe 已成功返回、弹窗却在 2ms 后按「探测超时」落定。
        // 现在探测本体跑在 Downloader 的共享 Deferred 上（见 probeShared，组合取消
        // 掐不死它），这里只 await：全链唯一的 20 秒封顶只捕 TimeoutCancellationException，
        // 其余 CancellationException 不吞（effect 真被取消就让它取消，重组后重新 await
        // 共享 Deferred 立刻拿到已完成的真实结果）。
        val result: Downloader.ProbeResult? = try {
            kotlinx.coroutines.withTimeout(20_000) {
                Downloader.probeShared(
                    url, null, PrivacyMode.cookieFor(url, privacy = false),
                    fresh = probeTick > 0,
                ).await()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Diag.log("NewDownloadSheet: probe wait timeout (20s) url=${url.take(100)}")
            null
        }
        size = result?.size ?: -1L
        probeMime = result?.mime
        probeError = result?.error ?: if (result == null) "探测超时" else null
        // 重定向后的最终地址回填：文件名与实际下载都以它为准
        if (!result?.finalUrl.isNullOrBlank()) {
            resolvedUrl = result.finalUrl
            if (result.finalUrl != url) onUrlResolved(result.finalUrl)
        }
        // 探测带回的 Content-Disposition 文件名只在用户还没手改时覆盖
        if (!nameEdited && !result?.fileName.isNullOrBlank()) fileName = result.fileName
        // 探测没给出文件名时，从签名直链的查询参数推导（release-assets 的 rscd 里藏着真名）
        if (!nameEdited && result?.fileName.isNullOrBlank()) {
            Downloader.fileNameFromQuery(url)?.let { fileName = it }
        }
        probing = false
        Diag.log("NewDownloadSheet: probe settled size=$size mime=$probeMime error=${probeError ?: "-"} fileName=$fileName")
        // 弹窗最终展示态（fork.16）：与界面渲染同式计算（大小行文案与主按钮文案），
        // diag 里可直接核对 settled 与用户实际看到的是否一致
        val displaySizeText = when {
            size >= 0 -> formatBytes(size)
            probeError != null -> "未知（探测失败）"
            else -> "未知"
        }
        val displayPrimary = if (probeError != null) "直接下载" else "下载"
        Diag.log("NewDownloadSheet: display fileName=$fileName sizeText=$displaySizeText primary=$displayPrimary")
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
                    probeError != null -> "未知（探测失败）"
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
                    onConfirm(fileName.ifBlank { Downloader.fileNameFromQuery(url) ?: Downloader.fileNameFrom(url, null) }, false)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(text = "添加", style = MiuixTheme.textStyles.button)
            }
            Spacer(Modifier.width(12.dp))
            AppDialogButton(
                onClick = {
                    onConfirm(fileName.ifBlank { Downloader.fileNameFromQuery(url) ?: Downloader.fileNameFrom(url, null) }, true)
                },
                modifier = Modifier.weight(1f),
                role = GlassDialogButtonRole.Primary,
            ) {
                // 兜底铁律（fork.15）：探测失败时主按钮明示「直接下载」——不依赖任何探测
                // 元数据（大小 / MIME / 最终地址），拿链接与推导文件名直接建任务开下；
                // 「在浏览器打开」永远只是附加出口，绝不是唯一出路。探测中也不锁按钮。
                Text(text = if (probeError != null) "直接下载" else "下载", style = MiuixTheme.textStyles.button)
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

/**
 * fork.18：下载确认改普通内联面板——直接画进本 Activity 自己的窗口，不再经
 * NewDownloadSheet → AppBottomSheet → miuix 覆盖层 sheet（GlassBottomSheet /
 * OverlayBottomSheet 靠独立 Dialog / Popup 窗口呈现）。fork.17 真机实证：本 Activity
 * 窗口已获焦（dialog window focused 双行俱在）、主题正常，唯独那套覆盖层内容自
 * fork.5 起从未在该机渲染出来过（状态 / 探测 / 日志全跑、画面不出）。本面板的组合树
 * 里只有基础内联组件（Box / Column / Row / Text / TextField / TextButton），探测状态
 * 机与 NewDownloadSheet 同一份口径：probeShared await（只捕 TimeoutCancellation-
 * Exception）、probe settled / display 两行 Diag 一字未改、fileNameFromQuery 兜底、
 * onUrlResolved 回填、onConfirm 建任务；NewDownloadSheet 本体保留不动。
 */
@Composable
fun DownloadConfirmPanel(
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
    var probeError by remember(url) { mutableStateOf<String?>(null) }
    var resolvedUrl by remember(url) { mutableStateOf(url) }
    var probeTick by remember(url) { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { Diag.log("DownloadDialogActivity: plain panel composed") }

    LaunchedEffect(url, probeTick) {
        probing = true
        probeError = null
        Diag.log("NewDownloadSheet: probe begin url=${url.take(100)}")
        // 与 NewDownloadSheet 相同的探测口径（fork.16）：只 await Downloader 的共享
        // Deferred，全链唯一的 20 秒封顶只捕 TimeoutCancellationException，其余取消不吞。
        val result: Downloader.ProbeResult? = try {
            kotlinx.coroutines.withTimeout(20_000) {
                Downloader.probeShared(
                    url, null, PrivacyMode.cookieFor(url, privacy = false),
                    fresh = probeTick > 0,
                ).await()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Diag.log("NewDownloadSheet: probe wait timeout (20s) url=${url.take(100)}")
            null
        }
        size = result?.size ?: -1L
        probeMime = result?.mime
        probeError = result?.error ?: if (result == null) "探测超时" else null
        // 重定向后的最终地址回填：文件名与实际下载都以它为准
        if (!result?.finalUrl.isNullOrBlank()) {
            resolvedUrl = result.finalUrl
            if (result.finalUrl != url) onUrlResolved(result.finalUrl)
        }
        // 探测带回的 Content-Disposition 文件名只在用户还没手改时覆盖
        if (!nameEdited && !result?.fileName.isNullOrBlank()) fileName = result.fileName
        // 探测没给出文件名时，从签名直链的查询参数推导（release-assets 的 rscd 里藏着真名）
        if (!nameEdited && result?.fileName.isNullOrBlank()) {
            Downloader.fileNameFromQuery(url)?.let { fileName = it }
        }
        probing = false
        Diag.log("NewDownloadSheet: probe settled size=$size mime=$probeMime error=${probeError ?: "-"} fileName=$fileName")
        // 弹窗最终展示态（fork.16）：与界面渲染同式计算，diag 里可直接核对
        val displaySizeText = when {
            size >= 0 -> formatBytes(size)
            probeError != null -> "未知（探测失败）"
            else -> "未知"
        }
        val displayPrimary = if (probeError != null) "直接下载" else "下载"
        Diag.log("NewDownloadSheet: display fileName=$fileName sizeText=$displaySizeText primary=$displayPrimary")
    }

    // 遮罩与面板都是本窗口内的普通内联布局：点遮罩关闭，面板本体消费点击不穿透
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Black.copy(alpha = 0.35f))
            .clickable { onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clickable { }
                .background(
                    MiuixTheme.colorScheme.surface,
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                )
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Text(
                text = "新建下载",
                fontSize = 19.sp,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(14.dp))
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
                        probeError != null -> "未知（探测失败）"
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
            // 逃生口：探测发现下到的其实是网页（text/html）时给一条回主界面的路
            if (!probing && probeMime?.startsWith("text/html") == true) {
                Spacer(Modifier.height(14.dp))
                TextButton(
                    text = "在浏览器打开",
                    onClick = { onOpenInBrowser(resolvedUrl) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    text = "添加",
                    onClick = {
                        onConfirm(fileName.ifBlank { Downloader.fileNameFromQuery(url) ?: Downloader.fileNameFrom(url, null) }, false)
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                TextButton(
                    // 兜底铁律（fork.15）：探测失败时主按钮明示「直接下载」，不依赖探测
                    // 元数据直接建任务开下；探测中也不锁按钮
                    text = if (probeError != null) "直接下载" else "下载",
                    onClick = {
                        onConfirm(fileName.ifBlank { Downloader.fileNameFromQuery(url) ?: Downloader.fileNameFrom(url, null) }, true)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
