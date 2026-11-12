package dev.dsh.miuixbrowser

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/**
 * TXT 文本编解码（fork.26）：自动识别 UTF-8 / GBK(GB18030) / UTF-16。
 *
 * 识别顺序：BOM → UTF-16 无 BOM 时的零字节分布启发 → UTF-8 严格解码
 * （报错即不是 UTF-8）→ GB18030 兜底。中文小说以 GBK 居多，UTF-8 严格
 * 解码失败后落 GB18030 即为正确结果，不会乱码。手动指定时按指定编码
 * 直接解码（UTF-16 无 BOM 默认小端，与国内小说来源一致）。
 */
object TxtCodec {

    const val ENCODING_AUTO = "自动"
    val ENCODING_OPTIONS = listOf(ENCODING_AUTO, "UTF-8", "GBK", "UTF-16")

    class Decoded(val text: String, val encoding: String)

    /** [override] 为 null 或「自动」时走识别；否则按指定编码解码 */
    fun decode(bytes: ByteArray, override: String?): Decoded {
        if (override != null && override != ENCODING_AUTO) {
            return Decoded(decodeWith(bytes, override), override)
        }
        return detect(bytes)
    }

    fun decodeWith(bytes: ByteArray, encoding: String): String {
        val text = when (encoding) {
            "GBK" -> String(bytes, charset("GB18030"))
            "UTF-16" -> {
                val cs = when {
                    hasBom(bytes, 0xFF, 0xFE) -> Charsets.UTF_16LE
                    hasBom(bytes, 0xFE, 0xFF) -> Charsets.UTF_16BE
                    else -> Charsets.UTF_16LE
                }
                String(bytes, cs)
            }
            else -> String(bytes, Charsets.UTF_8)
        }
        return text.removePrefix("\uFEFF")
    }

    private fun hasBom(bytes: ByteArray, b0: Int, b1: Int): Boolean =
        bytes.size >= 2 && bytes[0] == b0.toByte() && bytes[1] == b1.toByte()

    private fun detect(bytes: ByteArray): Decoded {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return Decoded(String(bytes, 3, bytes.size - 3, Charsets.UTF_8), "UTF-8")
        }
        if (hasBom(bytes, 0xFF, 0xFE)) {
            return Decoded(String(bytes, Charsets.UTF_16LE).removePrefix("\uFEFF"), "UTF-16")
        }
        if (hasBom(bytes, 0xFE, 0xFF)) {
            return Decoded(String(bytes, Charsets.UTF_16BE).removePrefix("\uFEFF"), "UTF-16")
        }
        detectUtf16ByZeros(bytes)?.let { csName ->
            return Decoded(String(bytes, charset(csName)).removePrefix("\uFEFF"), "UTF-16")
        }
        strictUtf8(bytes)?.let { return Decoded(it, "UTF-8") }
        return Decoded(String(bytes, charset("GB18030")), "GBK")
    }

    /**
     * 无 BOM 的 UTF-16 启发：ASCII 与常用汉字在某一字节位上大量为 0。
     * 小端时奇数位多 0、大端时偶数位多 0；要求一边占比 >30% 且明显压过另一边，
     * 避免把 GBK / UTF-8 文本误判进来。
     */
    private fun detectUtf16ByZeros(bytes: ByteArray): String? {
        val n = minOf(bytes.size, 1024) and 1.inv()
        if (n < 16) return null
        var zeroEven = 0
        var zeroOdd = 0
        var i = 0
        while (i < n) {
            if (bytes[i] == 0.toByte()) zeroEven++
            if (bytes[i + 1] == 0.toByte()) zeroOdd++
            i += 2
        }
        val pairs = n / 2
        val evenRatio = zeroEven.toFloat() / pairs
        val oddRatio = zeroOdd.toFloat() / pairs
        return when {
            oddRatio > 0.3f && oddRatio > evenRatio * 3 -> "UTF-16LE"
            evenRatio > 0.3f && evenRatio > oddRatio * 3 -> "UTF-16BE"
            else -> null
        }
    }

    private fun strictUtf8(bytes: ByteArray): String? = runCatching {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }.getOrNull()

    /** 正文分段：逐行 trim 后去掉空行，一行一段（txt 小说的通行排版） */
    fun paragraphsOf(text: String): List<String> =
        text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
}

data class TxtChapter(val title: String, val paragraphIndex: Int)

/**
 * 章节切分（fork.26）：标题行正则——「第X章 / 第X回 / 第X节 / 第X卷 …」为主，
 * 序章 / 楔子 / 番外 / 尾声等特殊标题为辅；标题行限长 50 字以内，
 * 降低正文里提到「第X章」时的误命中。
 */
object TxtChapters {

    private val MAIN = Regex("""^第\s*[0-9零一二三四五六七八九十百千万两]+\s*[章回节卷部篇集].*$""")
    private val SPECIAL = Regex("""^(序章|序言|楔子|前言|引言|尾声|后记|终章|大结局|完本感言)([\s:：、·—\-].*)?$""")
    private val EXTRA = Regex("""^番外[0-9零一二三四五六七八九十]*([\s:：、·—\-].*)?$""")

    fun split(paragraphs: List<String>): List<TxtChapter> {
        val out = ArrayList<TxtChapter>()
        for ((index, p) in paragraphs.withIndex()) {
            if (p.length > 50) continue
            if (MAIN.matches(p) || SPECIAL.matches(p) || EXTRA.matches(p)) {
                out += TxtChapter(p, index)
            }
        }
        return out
    }
}

/**
 * 阅读进度与设置（fork.26）。
 *
 * 进度按空间分开存：普通空间与隐私空间各一份 prefs 文件（与 fork.21 的
 * 下载隔离同口径——隐私空间里读过的文件，其进度与手动编码不落到普通
 * 空间可见的存储里）。文件标识 = uri + 字节数的 SHA-256 前 32 位，同一
 * 文件内容不变即可复用进度。字号是纯界面偏好、无内容信息，单独一份。
 */
object TxtReaderStore {

    private const val UI_PREFS = "marea_txt_reader_ui"
    private const val KEY_FONT = "font_size_sp"
    const val DEFAULT_FONT_SP = 19f

    private fun progressPrefs(context: Context, space: Int) =
        context.getSharedPreferences(
            if (space == 1) "marea_txt_reader_private" else "marea_txt_reader",
            Context.MODE_PRIVATE,
        )

    fun fileKey(uri: Uri, byteCount: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$uri|$byteCount".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 32)
    }

    data class Progress(val paragraph: Int, val offset: Int, val encoding: String?)

    fun loadProgress(context: Context, space: Int, key: String): Progress? {
        val raw = runCatching {
            progressPrefs(context, space).getString("p_$key", null)
        }.getOrNull() ?: return null
        val parts = raw.split('|')
        if (parts.size < 2) return null
        return Progress(
            paragraph = parts[0].toIntOrNull() ?: return null,
            offset = parts[1].toIntOrNull() ?: 0,
            encoding = parts.getOrNull(2)?.ifBlank { null },
        )
    }

    fun saveProgress(
        context: Context,
        space: Int,
        key: String,
        paragraph: Int,
        offset: Int,
        encoding: String?,
    ) {
        runCatching {
            progressPrefs(context, space).edit()
                .putString("p_$key", "$paragraph|$offset|${encoding.orEmpty()}")
                .apply()
        }
    }

    fun fontSize(context: Context): Float = runCatching {
        context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_FONT, DEFAULT_FONT_SP)
    }.getOrDefault(DEFAULT_FONT_SP)

    fun setFontSize(context: Context, value: Float) {
        runCatching {
            context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_FONT, value).apply()
        }
    }
}

/**
 * TXT 阅读器入口（fork.26）。三处汇合：
 * - 下载列表里点已完成的 .txt（显式意图，带任务所属空间）；
 * - 浏览器菜单「打开文件」走系统选择器后显式拉起（带当前空间）；
 * - 其他应用 VIEW 送来的 content:// / file:// text/plain（意图 data 直达，
 *   空间按当前隐私模式状态取）。http(s) 意图不经这里，仍归
 *   ExternalLinkActivity 的 fork.24/25 链路，本 Activity 的过滤器只声明
 *   content / file 两种 scheme，不与之重叠。
 */
class TxtReaderActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URI = "txt_reader_uri"
        const val EXTRA_NAME = "txt_reader_name"
        const val EXTRA_SPACE = "txt_reader_space"

        fun intent(context: Context, uri: Uri, name: String?, space: Int): Intent =
            Intent(context, TxtReaderActivity::class.java).apply {
                putExtra(EXTRA_URI, uri.toString())
                if (!name.isNullOrBlank()) putExtra(EXTRA_NAME, name)
                putExtra(EXTRA_SPACE, space)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        /** 下载列表判定：文件名 .txt 结尾或 MIME 为 text/plain 即进阅读器 */
        fun isTxt(name: String?, mime: String?): Boolean =
            name?.endsWith(".txt", ignoreCase = true) == true ||
                mime?.equals("text/plain", ignoreCase = true) == true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diag.init(this)
        val uri: Uri? = intent?.getStringExtra(EXTRA_URI)?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: intent?.data
        if (uri == null) {
            Diag.log("TxtReaderActivity: no uri, finish")
            finish()
            return
        }
        val space = when {
            intent?.hasExtra(EXTRA_SPACE) == true ->
                if (intent.getIntExtra(EXTRA_SPACE, 0) == 1) 1 else 0
            else -> if (PrivacyMode.active) 1 else 0
        }
        val name = intent?.getStringExtra(EXTRA_NAME)?.takeIf { it.isNotBlank() }
            ?: displayName(uri)
        Diag.log("TxtReaderActivity: open name=$name space=$space uri=${uri.toString().take(100)}")
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
                    TxtReaderScreen(uri = uri, name = name, space = space, onClose = { finish() })
                }
            }
        }
    }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) cursor.getString(idx)?.takeIf { it.isNotBlank() }?.let { return it }
                        }
                    }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "TXT 文档"
    }
}

private class TxtBook(
    val paragraphs: List<String>,
    val chapters: List<TxtChapter>,
    val chapterStarts: Set<Int>,
    val encodingLabel: String,
)

private fun readAllBytes(context: Context, uri: Uri): ByteArray {
    val stream = if (uri.scheme == ContentResolver.SCHEME_FILE) {
        File(uri.path ?: throw IllegalStateException("bad file uri")).inputStream()
    } else {
        context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("openInputStream returned null")
    }
    return stream.use { it.readBytes() }
}

@Composable
private fun TxtReaderScreen(uri: Uri, name: String, space: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var rawBytes by remember { mutableStateOf<ByteArray?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var book by remember { mutableStateOf<TxtBook?>(null) }
    var fileKey by remember { mutableStateOf<String?>(null) }
    var savedProgress by remember { mutableStateOf<TxtReaderStore.Progress?>(null) }
    var encodingChoice by remember { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf(false) }
    var jumpTo by remember { mutableStateOf<Int?>(null) }
    var fontSize by remember { mutableStateOf(TxtReaderStore.fontSize(context)) }
    var showSettings by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }

    // 读原始字节（只读一次；编码切换只重新解码，不重新读文件）
    LaunchedEffect(uri) {
        runCatching { withContext(Dispatchers.IO) { readAllBytes(context, uri) } }
            .onSuccess { bytes ->
                rawBytes = bytes
                val key = TxtReaderStore.fileKey(uri, bytes.size)
                fileKey = key
                val progress = TxtReaderStore.loadProgress(appContext, space, key)
                savedProgress = progress
                if (encodingChoice == null && progress?.encoding != null) {
                    encodingChoice = progress.encoding
                }
                Diag.log("TxtReader: loaded ${bytes.size} bytes, progress=${progress != null}")
            }
            .onFailure {
                Diag.log("TxtReader: read failed: ${it.message}")
                loadError = true
            }
    }

    // 解码 + 切章（编码变化时重跑，段落数不变则阅读位置按原段号保留）
    LaunchedEffect(rawBytes, encodingChoice) {
        val bytes = rawBytes ?: return@LaunchedEffect
        val decoded = withContext(Dispatchers.Default) {
            TxtCodec.decode(bytes, encodingChoice)
        }
        val paragraphs = TxtCodec.paragraphsOf(decoded.text)
        val chapters = TxtChapters.split(paragraphs)
        book = TxtBook(
            paragraphs = paragraphs,
            chapters = chapters,
            chapterStarts = chapters.map { it.paragraphIndex }.toHashSet(),
            encodingLabel = decoded.encoding,
        )
        Diag.log("TxtReader: decoded as ${decoded.encoding}, paragraphs=${paragraphs.size}, chapters=${chapters.size}")
    }

    // 首次成书：恢复进度；编码切换后成书：回到切换前的段落
    LaunchedEffect(book) {
        val b = book ?: return@LaunchedEffect
        if (b.paragraphs.isEmpty()) return@LaunchedEffect
        val jump = jumpTo
        if (jump != null) {
            jumpTo = null
            listState.scrollToItem(jump.coerceIn(0, b.paragraphs.size - 1))
        } else if (!restored) {
            restored = true
            val progress = savedProgress
            if (progress != null && progress.paragraph > 0) {
                listState.scrollToItem(
                    progress.paragraph.coerceIn(0, b.paragraphs.size - 1),
                    progress.offset,
                )
            }
        }
    }

    // 滚动即存进度（恢复完成前不写，避免把初始 0 位覆盖掉已存进度）
    LaunchedEffect(listState, fileKey, encodingChoice) {
        val key = fileKey ?: return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                if (restored) {
                    TxtReaderStore.saveProgress(
                        appContext, space, key, index, offset,
                        encodingChoice?.takeIf { it != TxtCodec.ENCODING_AUTO },
                    )
                }
            }
    }
    DisposableEffect(Unit) {
        onDispose {
            val key = fileKey
            if (key != null && restored) {
                TxtReaderStore.saveProgress(
                    appContext, space, key,
                    listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset,
                    encodingChoice?.takeIf { it != TxtCodec.ENCODING_AUTO },
                )
            }
        }
    }

    val currentBook = book
    val total = currentBook?.paragraphs?.size ?: 0
    val firstVisible = listState.firstVisibleItemIndex
    val currentChapter = currentBook?.chapters?.lastOrNull { it.paragraphIndex <= firstVisible }?.title
    val percent = if (total > 0) (firstVisible * 100 / total) else 0

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = name,
                subtitle = when {
                    loadError -> ""
                    currentBook == null -> "加载中…"
                    currentChapter != null -> currentChapter
                    else -> ""
                },
                navigationIcon = {
                    LiquidGlassBackButton(backdrop = null, surfaceVisible = true, onClick = onClose)
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = "目录",
                    onClick = { showToc = true },
                    enabled = (currentBook?.chapters?.size ?: 0) > 0,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (currentBook != null) "${currentBook.encodingLabel} · $percent%" else "",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    text = "设置",
                    onClick = { showSettings = true },
                    enabled = currentBook != null,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MiuixTheme.colorScheme.surface),
        ) {
            when {
                loadError -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "无法打开这个文件",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                currentBook == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "加载中…",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                currentBook.paragraphs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "文件是空的，或当前编码下没有可显示的文字。\n可在「设置」里切换编码重试。",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(currentBook.paragraphs.size) { index ->
                        val isChapter = index in currentBook.chapterStarts
                        Text(
                            text = currentBook.paragraphs[index],
                            style = TextStyle(
                                fontSize = if (isChapter) (fontSize + 2).sp else fontSize.sp,
                                lineHeight = ((if (isChapter) fontSize + 2 else fontSize) * 1.75f).sp,
                                fontWeight = if (isChapter) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = if (isChapter && index > 0) 18.dp else 0.dp, bottom = 7.dp),
                        )
                    }
                }
            }

            // 阅读设置面板（页内覆盖层，不走系统对话框）
            if (showSettings && currentBook != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable { showSettings = false },
                ) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(12.dp)
                            .clickable(enabled = false) {},
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "字号", fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = fontSize.toInt().toString(),
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Slider(
                                value = fontSize,
                                onValueChange = { fontSize = it },
                                valueRange = 15f..30f,
                                onValueChangeFinished = { TxtReaderStore.setFontSize(appContext, fontSize) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(text = "编码", fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(8.dp))
                            Row {
                                val selected = encodingChoice ?: TxtCodec.ENCODING_AUTO
                                TxtCodec.ENCODING_OPTIONS.forEach { option ->
                                    val isSelected = option == selected
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) MiuixTheme.colorScheme.primary
                                                else MiuixTheme.colorScheme.surfaceVariant,
                                            )
                                            .clickable {
                                                if (option != selected) {
                                                    jumpTo = listState.firstVisibleItemIndex
                                                    encodingChoice = option
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 7.dp),
                                    ) {
                                        Text(
                                            text = option,
                                            fontSize = 13.sp,
                                            color = if (isSelected) MiuixTheme.colorScheme.onPrimary
                                            else MiuixTheme.colorScheme.onSurface,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "当前识别为 ${currentBook.encodingLabel}；识别不对时手动切换，会按新编码重新解码。",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            if (currentBook.chapters.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                TextButton(
                                    text = "章节目录（共 ${currentBook.chapters.size} 章）",
                                    onClick = {
                                        showSettings = false
                                        showToc = true
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            // 章节目录（页内全屏覆盖层）
            if (showToc && currentBook != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.surface),
                ) {
                    SmallTopAppBar(
                        title = "目录",
                        subtitle = name,
                        navigationIcon = {
                            LiquidGlassBackButton(backdrop = null, surfaceVisible = true, onClick = { showToc = false })
                        },
                    )
                    if (currentBook.chapters.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "没有识别到章节标题",
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(currentBook.chapters.size) { index ->
                                val chapter = currentBook.chapters[index]
                                val isCurrent = chapter.title == currentChapter
                                Text(
                                    text = chapter.title,
                                    fontSize = 14.sp,
                                    color = if (isCurrent) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showToc = false
                                            scope.launch { listState.scrollToItem(chapter.paragraphIndex) }
                                        }
                                        .padding(horizontal = 20.dp, vertical = 13.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
