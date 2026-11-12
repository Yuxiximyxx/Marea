package dev.dsh.miuixbrowser

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlin.math.roundToInt

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
 * 一条目录（切章）规则（fork.27）。字段语义与 Legado 的 TxtTocRule 对齐：
 * name = 规则名，pattern 对应 Legado 的 chapterRule（导入时 volumeRule
 * 非空会并入同一条的备选分支），enabled 对应 enable，列表顺序即优先级
 * （对应 serialNumber 的排序语义）。实现为 Marea 自有代码，仅格式兼容。
 */
data class TxtTocRule(
    val id: Long,
    val name: String,
    val pattern: String,
    val enabled: Boolean,
)

/**
 * 目录规则的存储与导入导出（fork.27）。
 *
 * 规则是纯配置（不含任何书籍内容），存全局 UI 偏好一份，不分空间。
 * 导入兼容三种来源：
 * - Legado 目录规则 JSON（数组或单对象）：字段 name / chapterRule
 *   （兼容旧字段名 rule）/ volumeRule / enable，逐条映射；
 * - Marea 自有导出 JSON（同字段写出，可回导、也可被 Legado 读）；
 * - 纯文本：一行一条，「名称|正则」或裸正则（# 开头为注释行跳过）。
 * 导入结果逐条校验正则可编译，坏条目计入失败数、不落库。
 */
object TxtTocRules {

    private const val KEY_RULES = "toc_rules_json"

    /** v1 的内置切章口径拆成的两条默认规则，首次使用时即此列表 */
    fun defaults(): List<TxtTocRule> = listOf(
        TxtTocRule(
            id = 1L,
            name = "第X章",
            pattern = """^第\s*[0-9零一二三四五六七八九十百千万两]+\s*[章回节卷部篇集].*$""",
            enabled = true,
        ),
        TxtTocRule(
            id = 2L,
            name = "特殊标题",
            pattern = """^(序章|序言|楔子|前言|引言|尾声|后记|终章|大结局|完本感言)([\s:：、·—\-].*)?$|^番外[0-9零一二三四五六七八九十]*([\s:：、·—\-].*)?$""",
            enabled = true,
        ),
    )

    fun load(context: Context): List<TxtTocRule> {
        val raw = runCatching {
            context.getSharedPreferences(TxtReaderStore.UI_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_RULES, null)
        }.getOrNull() ?: return defaults()
        return runCatching {
            val array = JSONArray(raw)
            val out = ArrayList<TxtTocRule>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val pattern = o.optString("pattern")
                if (pattern.isBlank()) continue
                out += TxtTocRule(
                    id = o.optLong("id", (i + 1).toLong()),
                    name = o.optString("name").ifBlank { "规则 ${i + 1}" },
                    pattern = pattern,
                    enabled = o.optBoolean("enabled", true),
                )
            }
            if (out.isEmpty()) defaults() else out
        }.getOrDefault(defaults())
    }

    fun save(context: Context, rules: List<TxtTocRule>) {
        runCatching {
            val array = JSONArray()
            rules.forEach { rule ->
                array.put(
                    JSONObject()
                        .put("id", rule.id)
                        .put("name", rule.name)
                        .put("pattern", rule.pattern)
                        .put("enabled", rule.enabled),
                )
            }
            context.getSharedPreferences(TxtReaderStore.UI_PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_RULES, array.toString()).apply()
        }
    }

    private val compileCache = HashMap<String, Regex?>()

    /** 编译规则正则；非法返回 null（调用方跳过该条） */
    fun compiled(rule: TxtTocRule): Regex? =
        compileCache.getOrPut(rule.pattern) { runCatching { Regex(rule.pattern) }.getOrNull() }

    fun isValidPattern(pattern: String): Boolean =
        pattern.isNotBlank() && runCatching { Regex(pattern) }.isSuccess

    data class ImportResult(val rules: List<TxtTocRule>, val failed: Int)

    /**
     * 宽容解析导入文本（格式口径见类注释）。[baseId] 起分配新 id，
     * 导入的规则一律追加语义（由调用方拼到现有列表末尾）。
     */
    fun parseImport(text: String, baseId: Long): ImportResult {
        val trimmed = text.trim()
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            val items = ArrayList<JSONObject>()
            var failed = 0
            val parsed = runCatching {
                if (trimmed.startsWith("[")) {
                    val array = JSONArray(trimmed)
                    for (i in 0 until array.length()) {
                        array.optJSONObject(i)?.let { items += it } ?: run { failed++ }
                    }
                } else {
                    items += JSONObject(trimmed)
                }
            }
            // 整体不是合法 JSON：按纯文本逐行再试一次，别直接判死
            if (parsed.isFailure) return parseLines(trimmed, baseId)
            val out = ArrayList<TxtTocRule>()
            var seq = 0L
            for (o in items) {
                val chapter = o.optString("chapterRule").ifBlank { o.optString("rule") }
                val volume = o.optString("volumeRule")
                if (chapter.isBlank()) {
                    failed++
                    continue
                }
                val pattern = when {
                    volume.isBlank() || volume == chapter -> chapter
                    else -> "(?:$chapter)|(?:$volume)"
                }
                if (!isValidPattern(pattern)) {
                    failed++
                    continue
                }
                seq++
                out += TxtTocRule(
                    baseId + seq,
                    o.optString("name").ifBlank { "导入规则 $seq" },
                    pattern,
                    o.optBoolean("enable", true),
                )
            }
            return ImportResult(out, failed)
        }
        return parseLines(trimmed, baseId)
    }

    private fun parseLines(text: String, baseId: Long): ImportResult {
        val out = ArrayList<TxtTocRule>()
        var failed = 0
        var seq = 0L
        for (rawLine in text.split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val sep = line.indexOf('|')
            val name: String
            val pattern: String
            if (sep > 0) {
                name = line.substring(0, sep).trim()
                pattern = line.substring(sep + 1).trim()
            } else {
                name = ""
                pattern = line
            }
            if (!isValidPattern(pattern)) {
                failed++
                continue
            }
            seq++
            out += TxtTocRule(
                baseId + seq,
                name.ifBlank { "导入规则 $seq" },
                pattern,
                enabled = true,
            )
        }
        return ImportResult(out, failed)
    }

    /** 导出为 Legado 同字段 JSON（name / chapterRule / volumeRule / enable / serialNumber） */
    fun exportJson(rules: List<TxtTocRule>): String {
        val array = JSONArray()
        rules.forEachIndexed { index, rule ->
            array.put(
                JSONObject()
                    .put("name", rule.name)
                    .put("chapterRule", rule.pattern)
                    .put("volumeRule", "")
                    .put("enable", rule.enabled)
                    .put("serialNumber", index),
            )
        }
        return array.toString(2)
    }
}

/**
 * 章节切分（fork.27 改为规则驱动）：逐段按规则列表顺序试，
 * 首条命中（启用中且正则合法）即定为章节标题；标题行仍保留
 * 50 字长度上限作固定护栏，防止正文长句误命中。
 */
object TxtChapters {

    const val MAX_TITLE_LENGTH = 50

    fun split(paragraphs: List<String>, rules: List<TxtTocRule>): List<TxtChapter> {
        val active = rules.filter { it.enabled }.mapNotNull { rule ->
            TxtTocRules.compiled(rule)?.let { rule to it }
        }
        if (active.isEmpty()) return emptyList()
        val out = ArrayList<TxtChapter>()
        for ((index, p) in paragraphs.withIndex()) {
            if (p.length > MAX_TITLE_LENGTH) continue
            if (active.any { (_, regex) -> regex.containsMatchIn(p) }) {
                out += TxtChapter(p, index)
            }
        }
        return out
    }
}

/**
 * 阅读进度与设置（fork.26，fork.27 扩展）。
 *
 * 进度按空间分开存：普通空间与隐私空间各一份 prefs 文件（与 fork.21 的
 * 下载隔离同口径——隐私空间里读过的文件，其进度与手动编码不落到普通
 * 空间可见的存储里）。文件标识 = uri + 字节数的 SHA-256 前 32 位，同一
 * 文件内容不变即可复用进度。字号、阅读模式、全屏开关与目录规则是纯
 * 界面/配置项、无内容信息，单独存全局 UI 偏好一份。
 *
 * 进度格式 v2：「段落|滚动像素偏移|段内字符偏移|手动编码|总段数」。
 * 字符偏移是翻页模式的精确位置，滚动模式上报时归 0（段落粒度）；
 * 两种模式互切都以（段落, 字符偏移）为统一基准互相定位。v1 的三段
 * 格式（段落|像素|编码）可兼容读入。
 */
object TxtReaderStore {

    const val UI_PREFS = "marea_txt_reader_ui"
    private const val KEY_FONT = "font_size_sp"
    private const val KEY_MODE = "read_mode"
    private const val KEY_FULLSCREEN = "fullscreen"
    const val DEFAULT_FONT_SP = 19f

    const val MODE_SCROLL = "scroll"
    const val MODE_PAGE = "page"

    private fun progressPrefs(context: Context, space: Int) =
        context.getSharedPreferences(
            if (space == 1) "marea_txt_reader_private" else "marea_txt_reader",
            Context.MODE_PRIVATE,
        )

    private fun uiPrefs(context: Context) =
        context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)

    fun fileKey(uri: Uri, byteCount: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$uri|$byteCount".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.substring(0, 32)
    }

    data class Progress(
        val paragraph: Int,
        val offsetPx: Int,
        val charOffset: Int,
        val encoding: String?,
        val total: Int,
    )

    fun loadProgress(context: Context, space: Int, key: String): Progress? {
        val raw = runCatching {
            progressPrefs(context, space).getString("p_$key", null)
        }.getOrNull() ?: return null
        val parts = raw.split('|')
        if (parts.size < 2) return null
        val paragraph = parts[0].toIntOrNull() ?: return null
        val offsetPx = parts[1].toIntOrNull() ?: 0
        // v2 第 3 段是数字字符偏移、v1 第 3 段是编码名，据此区分
        val charOffset = parts.getOrNull(2)?.toIntOrNull()
        return if (charOffset != null) {
            Progress(
                paragraph = paragraph,
                offsetPx = offsetPx,
                charOffset = charOffset,
                encoding = parts.getOrNull(3)?.ifBlank { null },
                total = parts.getOrNull(4)?.toIntOrNull() ?: 0,
            )
        } else {
            Progress(
                paragraph = paragraph,
                offsetPx = offsetPx,
                charOffset = 0,
                encoding = parts.getOrNull(2)?.ifBlank { null },
                total = 0,
            )
        }
    }

    fun saveProgress(
        context: Context,
        space: Int,
        key: String,
        paragraph: Int,
        offsetPx: Int,
        charOffset: Int,
        encoding: String?,
        total: Int,
    ) {
        runCatching {
            progressPrefs(context, space).edit()
                .putString("p_$key", "$paragraph|$offsetPx|$charOffset|${encoding.orEmpty()}|$total")
                .apply()
        }
    }

    fun fontSize(context: Context): Float = runCatching {
        uiPrefs(context).getFloat(KEY_FONT, DEFAULT_FONT_SP)
    }.getOrDefault(DEFAULT_FONT_SP)

    fun setFontSize(context: Context, value: Float) {
        runCatching { uiPrefs(context).edit().putFloat(KEY_FONT, value).apply() }
    }

    fun readMode(context: Context): String = runCatching {
        uiPrefs(context).getString(KEY_MODE, MODE_SCROLL) ?: MODE_SCROLL
    }.getOrDefault(MODE_SCROLL)

    fun setReadMode(context: Context, mode: String) {
        runCatching { uiPrefs(context).edit().putString(KEY_MODE, mode).apply() }
    }

    /** 全屏阅读开关：默认开（进入阅读即沉浸），设置里可关，选择持久化 */
    fun fullscreen(context: Context): Boolean = runCatching {
        uiPrefs(context).getBoolean(KEY_FULLSCREEN, true)
    }.getOrDefault(true)

    fun setFullscreen(context: Context, value: Boolean) {
        runCatching { uiPrefs(context).edit().putBoolean(KEY_FULLSCREEN, value).apply() }
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

/* ---------------- 翻页模式：按可视区域把正文切成页（fork.27） ---------------- */

/** 一页里的一段（或一段的一部分）：[startChar] 是它在原段落内的字符起点 */
private data class TxtSlice(
    val paragraph: Int,
    val text: String,
    val chapter: Boolean,
    val startChar: Int,
)

private data class TxtPage(val slices: List<TxtSlice>) {
    val startParagraph: Int get() = slices.firstOrNull()?.paragraph ?: 0
    val startChar: Int get() = slices.firstOrNull()?.startChar ?: 0
}

/**
 * 用 TextMeasurer 按当前字号/行高逐段逐行量高，贪心装页；段落可跨页拆分，
 * 拆分点用行首字符索引表达，保证页与段落/字符偏移互相可定位。
 * 段间距沿用滚动模式口径：段后 7dp、章节段前 18dp（页首章节不加前距）。
 */
private fun paginateTxt(
    measurer: androidx.compose.ui.text.TextMeasurer,
    paragraphs: List<String>,
    chapterStarts: Set<Int>,
    fontSizeSp: Float,
    contentWidthPx: Int,
    contentHeightPx: Int,
    paragraphBottomPx: Float,
    chapterTopPx: Float,
): List<TxtPage> {
    if (paragraphs.isEmpty() || contentWidthPx <= 0 || contentHeightPx <= 0) {
        return listOf(TxtPage(emptyList()))
    }
    val pages = ArrayList<TxtPage>()
    var pageSlices = ArrayList<TxtSlice>()
    var usedPx = 0f

    fun flushPage() {
        if (pageSlices.isNotEmpty()) {
            pages += TxtPage(pageSlices)
            pageSlices = ArrayList()
            usedPx = 0f
        }
    }

    for (i in paragraphs.indices) {
        val chapter = i in chapterStarts
        val size = if (chapter) fontSizeSp + 2 else fontSizeSp
        val style = TextStyle(fontSize = size.sp, lineHeight = (size * 1.75f).sp)
        val layout = measurer.measure(
            text = paragraphs[i],
            style = style,
            constraints = Constraints(maxWidth = contentWidthPx),
        )
        val totalLines = layout.lineCount
        var line = 0
        var firstChunk = true
        while (line < totalLines) {
            if (firstChunk && chapter && pageSlices.isNotEmpty()) {
                usedPx += chapterTopPx
            }
            val avail = contentHeightPx - usedPx
            var take = 0
            var h = 0f
            while (line + take < totalLines) {
                val lh = layout.getLineBottom(line + take) - layout.getLineTop(line + take)
                if (take > 0 && h + lh > avail) break
                h += lh
                take++
                if (h > avail) break // 单行比整页还高：强制放一行，避免死循环
            }
            if (take == 0) {
                flushPage()
                continue
            }
            val startChar = layout.getLineStart(line)
            val endChar = layout.getLineEnd(line + take - 1, true)
            pageSlices += TxtSlice(i, paragraphs[i].substring(startChar, endChar), chapter, startChar)
            usedPx += h
            line += take
            firstChunk = false
            if (line >= totalLines) {
                usedPx += paragraphBottomPx
            } else {
                flushPage()
            }
        }
    }
    flushPage()
    return if (pages.isEmpty()) listOf(TxtPage(emptyList())) else pages
}

/** 找最后一个起点不晚于 (paragraph, char) 的页，即该位置所在的页 */
private fun findTxtPage(pages: List<TxtPage>, paragraph: Int, char: Int): Int {
    var idx = 0
    for (i in pages.indices) {
        val p = pages[i]
        if (p.startParagraph < paragraph || (p.startParagraph == paragraph && p.startChar <= char)) {
            idx = i
        } else {
            break
        }
    }
    return idx
}

/* ---------------- 阅读屏（fork.27：规则目录 / 翻页 / 全屏） ---------------- */

private data class RuleEditRequest(val id: Long?, val name: String, val pattern: String)

@Composable
private fun TxtReaderScreen(uri: Uri, name: String, space: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val clipboard = LocalClipboardManager.current

    var rawBytes by remember { mutableStateOf<ByteArray?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var book by remember { mutableStateOf<TxtBook?>(null) }
    var bookVersion by remember { mutableIntStateOf(0) }
    var fileKey by remember { mutableStateOf<String?>(null) }
    var savedProgress by remember { mutableStateOf<TxtReaderStore.Progress?>(null) }
    var encodingChoice by remember { mutableStateOf<String?>(null) }
    var tocRules by remember { mutableStateOf(TxtTocRules.load(context)) }

    var fontSize by remember { mutableStateOf(TxtReaderStore.fontSize(context)) }
    var readMode by remember { mutableStateOf(TxtReaderStore.readMode(context)) }
    var fullscreen by remember { mutableStateOf(TxtReaderStore.fullscreen(context)) }
    var menuVisible by remember { mutableStateOf(!fullscreen) }

    // 统一位置锚点：段落 + 段内字符偏移，两种阅读模式共用（见 TxtReaderStore 注释）
    var anchorPara by remember { mutableIntStateOf(0) }
    var anchorChar by remember { mutableIntStateOf(0) }
    var scrollPx by remember { mutableIntStateOf(0) }
    var positionReady by remember { mutableStateOf(false) }
    var jumpPara by remember { mutableIntStateOf(-1) }
    var jumpSignal by remember { mutableIntStateOf(0) }
    var pageInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    var showSettings by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var ruleEditor by remember { mutableStateOf<RuleEditRequest?>(null) }
    var showPaste by remember { mutableStateOf(false) }
    var importReport by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    fun applyRules(newRules: List<TxtTocRule>) {
        tocRules = newRules
        TxtTocRules.save(appContext, newRules)
    }

    fun importRulesFromText(text: String) {
        val result = TxtTocRules.parseImport(text, System.currentTimeMillis())
        if (result.rules.isNotEmpty()) applyRules(tocRules + result.rules)
        importReport = "导入完成：成功 ${result.rules.size} 条，失败 ${result.failed} 条（已追加到列表末尾）"
        Diag.log("TxtReader rules import: ok=${result.rules.size} failed=${result.failed}")
    }

    val ruleImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { picked ->
        if (picked != null) {
            scope.launch {
                val text = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(picked)?.use { it.readBytes() }
                            ?.let { String(it, Charsets.UTF_8) }
                    }
                }.getOrNull()
                if (text == null) {
                    importReport = "导入失败：读不到所选文件"
                } else {
                    importRulesFromText(text)
                }
            }
        }
    }
    val ruleExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { target ->
        val content = pendingExport
        pendingExport = null
        if (target != null && content != null) {
            scope.launch {
                val ok = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(target)
                            ?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                    }
                }.isSuccess
                Toast.makeText(
                    context,
                    if (ok) "规则已导出" else "导出失败",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // 读原始字节（只读一次；编码/规则变化只重新解码切章，不重新读文件）
    LaunchedEffect(uri) {
        runCatching { withContext(Dispatchers.IO) { readAllBytes(context, uri) } }
            .onSuccess { bytes ->
                rawBytes = bytes
                val key = TxtReaderStore.fileKey(uri, bytes.size)
                fileKey = key
                val progress = TxtReaderStore.loadProgress(appContext, space, key)
                savedProgress = progress
                if (progress != null) {
                    anchorPara = progress.paragraph
                    anchorChar = progress.charOffset
                    scrollPx = progress.offsetPx
                }
                if (encodingChoice == null && progress?.encoding != null) {
                    encodingChoice = progress.encoding
                }
                // 打开过即入架（fork.27 书架），空间与进度同口径分开
                TxtBookshelfStore.upsert(appContext, space, key, uri.toString(), name, bytes.size)
                Diag.log("TxtReader: loaded ${bytes.size} bytes, progress=${progress != null}")
            }
            .onFailure {
                Diag.log("TxtReader: read failed: ${it.message}")
                loadError = true
            }
    }

    // 解码 + 按当前规则切章（编码或规则变化时重跑）
    LaunchedEffect(rawBytes, encodingChoice, tocRules) {
        val bytes = rawBytes ?: return@LaunchedEffect
        val decoded = withContext(Dispatchers.Default) {
            TxtCodec.decode(bytes, encodingChoice)
        }
        val paragraphs = TxtCodec.paragraphsOf(decoded.text)
        val chapters = TxtChapters.split(paragraphs, tocRules)
        book = TxtBook(
            paragraphs = paragraphs,
            chapters = chapters,
            chapterStarts = chapters.map { it.paragraphIndex }.toHashSet(),
            encodingLabel = decoded.encoding,
        )
        bookVersion++
        Diag.log("TxtReader: decoded as ${decoded.encoding}, paragraphs=${paragraphs.size}, chapters=${chapters.size}")
    }

    // 位置锚点变化即存进度（positionReady 前不写，避免初始 0 位覆盖存量进度）
    LaunchedEffect(fileKey, readMode, encodingChoice) {
        val key = fileKey ?: return@LaunchedEffect
        snapshotFlow { Triple(anchorPara, anchorChar, scrollPx) }
            .distinctUntilChanged()
            .collect { (p, c, px) ->
                if (positionReady) {
                    TxtReaderStore.saveProgress(
                        appContext, space, key, p,
                        if (readMode == TxtReaderStore.MODE_PAGE) 0 else px,
                        c,
                        encodingChoice?.takeIf { it != TxtCodec.ENCODING_AUTO },
                        book?.paragraphs?.size ?: savedProgress?.total ?: 0,
                    )
                }
            }
    }
    DisposableEffect(Unit) {
        onDispose {
            val key = fileKey
            if (key != null && positionReady) {
                TxtReaderStore.saveProgress(
                    appContext, space, key, anchorPara,
                    if (readMode == TxtReaderStore.MODE_PAGE) 0 else scrollPx,
                    anchorChar,
                    encodingChoice?.takeIf { it != TxtCodec.ENCODING_AUTO },
                    book?.paragraphs?.size ?: savedProgress?.total ?: 0,
                )
            }
        }
    }

    // 全屏阅读：沉浸式隐藏系统栏；菜单呼出时临时显示（fork.27）
    LaunchedEffect(fullscreen, menuVisible) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (fullscreen && !menuVisible) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val window = (context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val currentBook = book
    val total = currentBook?.paragraphs?.size ?: 0
    val currentChapter = currentBook?.chapters?.lastOrNull { it.paragraphIndex <= anchorPara }?.title
    val percent = if (total > 0) anchorPara * 100 / total else 0

    val topBar: @Composable () -> Unit = {
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
    }
    val bottomBar: @Composable () -> Unit = {
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
                text = when {
                    currentBook == null -> ""
                    readMode == TxtReaderStore.MODE_PAGE && pageInfo != null ->
                        "${pageInfo!!.first} / ${pageInfo!!.second} 页 · ${currentBook.encodingLabel}"
                    else -> "${currentBook.encodingLabel} · $percent%"
                },
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
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
    ) {
        when {
            loadError -> Column(Modifier.fillMaxSize()) {
                topBar()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "无法打开这个文件",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            currentBook == null -> Column(Modifier.fillMaxSize()) {
                topBar()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "加载中…",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            currentBook.paragraphs.isEmpty() -> Column(Modifier.fillMaxSize()) {
                topBar()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "文件是空的，或当前编码下没有可显示的文字。\n可在「设置」里切换编码重试。",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            else -> {
                val content: @Composable (Modifier) -> Unit = { contentModifier ->
                    key(bookVersion) {
                        if (readMode == TxtReaderStore.MODE_PAGE) {
                            TxtPageContent(
                                book = currentBook,
                                fontSize = fontSize,
                                anchorPara = anchorPara,
                                anchorChar = anchorChar,
                                jumpPara = jumpPara,
                                jumpSignal = jumpSignal,
                                onPosition = { p, c ->
                                    anchorPara = p
                                    anchorChar = c
                                    positionReady = true
                                },
                                onPageInfo = { cur, tot -> pageInfo = cur to tot },
                                onTapCenter = { menuVisible = !menuVisible },
                                modifier = contentModifier,
                            )
                        } else {
                            TxtScrollContent(
                                book = currentBook,
                                fontSize = fontSize,
                                initialPara = anchorPara,
                                initialPx = when {
                                    anchorChar > 0 -> 0
                                    positionReady -> scrollPx
                                    else -> savedProgress?.offsetPx ?: 0
                                },
                                jumpPara = jumpPara,
                                jumpSignal = jumpSignal,
                                tapToggleEnabled = fullscreen,
                                onToggleMenu = { menuVisible = !menuVisible },
                                onPosition = { p, px ->
                                    anchorPara = p
                                    anchorChar = 0
                                    scrollPx = px
                                    positionReady = true
                                },
                                modifier = contentModifier,
                            )
                        }
                    }
                }
                if (fullscreen) {
                    content(Modifier.fillMaxSize())
                    if (menuVisible) {
                        Column(Modifier.fillMaxSize()) {
                            topBar()
                            Spacer(Modifier.weight(1f))
                            bottomBar()
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        topBar()
                        content(Modifier.weight(1f))
                        bottomBar()
                    }
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
                    Column(Modifier.verticalScroll(rememberScrollState())) {
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
                        Text(text = "阅读方式", fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            listOf(
                                TxtReaderStore.MODE_SCROLL to "上下滚动",
                                TxtReaderStore.MODE_PAGE to "左右翻页",
                            ).forEach { (value, label) ->
                                val isSelected = readMode == value
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) MiuixTheme.colorScheme.primary
                                            else MiuixTheme.colorScheme.surfaceVariant,
                                        )
                                        .clickable {
                                            if (readMode != value) {
                                                readMode = value
                                                if (value == TxtReaderStore.MODE_SCROLL) pageInfo = null
                                                TxtReaderStore.setReadMode(appContext, value)
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        color = if (isSelected) MiuixTheme.colorScheme.onPrimary
                                        else MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(text = "全屏阅读", fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            listOf(true to "开", false to "关").forEach { (value, label) ->
                                val isSelected = fullscreen == value
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) MiuixTheme.colorScheme.primary
                                            else MiuixTheme.colorScheme.surfaceVariant,
                                        )
                                        .clickable {
                                            if (fullscreen != value) {
                                                fullscreen = value
                                                if (!value) menuVisible = true
                                                TxtReaderStore.setFullscreen(appContext, value)
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        color = if (isSelected) MiuixTheme.colorScheme.onPrimary
                                        else MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "全屏时点按正文呼出菜单",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
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
                                            if (option != selected) encodingChoice = option
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
                        Spacer(Modifier.height(12.dp))
                        TextButton(
                            text = "目录规则（共 ${tocRules.size} 条，启用 ${tocRules.count { it.enabled }} 条）",
                            onClick = {
                                showSettings = false
                                importReport = null
                                showRules = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (currentBook.chapters.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
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
                            text = "没有识别到章节标题，可在「设置 → 目录规则」里调整规则",
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
                                        jumpPara = chapter.paragraphIndex
                                        jumpSignal++
                                    }
                                    .padding(horizontal = 20.dp, vertical = 13.dp),
                            )
                        }
                    }
                }
            }
        }

        // 目录规则管理（页内全屏覆盖层，fork.27）
        if (showRules) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface),
            ) {
                SmallTopAppBar(
                    title = "目录规则",
                    subtitle = "按列表顺序匹配，首中即为章节",
                    navigationIcon = {
                        LiquidGlassBackButton(backdrop = null, surfaceVisible = true, onClick = { showRules = false })
                    },
                )
                if (importReport != null) {
                    Text(
                        text = importReport!!,
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(tocRules.size) { index ->
                        val rule = tocRules[index]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            insideMargin = PaddingValues(14.dp),
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = rule.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (rule.enabled) MiuixTheme.colorScheme.primary
                                                else MiuixTheme.colorScheme.surfaceVariant,
                                            )
                                            .clickable {
                                                applyRules(
                                                    tocRules.map {
                                                        if (it.id == rule.id) it.copy(enabled = !it.enabled) else it
                                                    },
                                                )
                                            }
                                            .padding(horizontal = 12.dp, vertical = 5.dp),
                                    ) {
                                        Text(
                                            text = if (rule.enabled) "启用" else "停用",
                                            fontSize = 12.sp,
                                            color = if (rule.enabled) MiuixTheme.colorScheme.onPrimary
                                            else MiuixTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = rule.pattern,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row {
                                    TextButton(
                                        text = "编辑",
                                        onClick = { ruleEditor = RuleEditRequest(rule.id, rule.name, rule.pattern) },
                                    )
                                    TextButton(
                                        text = "上移",
                                        enabled = index > 0,
                                        onClick = {
                                            val next = tocRules.toMutableList()
                                            val tmp = next[index - 1]
                                            next[index - 1] = next[index]
                                            next[index] = tmp
                                            applyRules(next)
                                        },
                                    )
                                    TextButton(
                                        text = "下移",
                                        enabled = index < tocRules.size - 1,
                                        onClick = {
                                            val next = tocRules.toMutableList()
                                            val tmp = next[index + 1]
                                            next[index + 1] = next[index]
                                            next[index] = tmp
                                            applyRules(next)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row {
                        TextButton(
                            text = "新增规则",
                            onClick = { ruleEditor = RuleEditRequest(null, "", "") },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = "从文件导入",
                            onClick = { ruleImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = "粘贴导入",
                            onClick = { showPaste = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row {
                        TextButton(
                            text = "复制导出",
                            onClick = {
                                clipboard.setText(AnnotatedString(TxtTocRules.exportJson(tocRules)))
                                Toast.makeText(context, "规则 JSON 已复制", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = "导出到文件",
                            onClick = {
                                pendingExport = TxtTocRules.exportJson(tocRules)
                                ruleExportLauncher.launch("marea-toc-rules.json")
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = "导出格式与 Legado 目录规则互通（name / chapterRule 字段）；改规则后当前书目录立即按新规则重算。",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // 规则编辑（新增/编辑共用，fork.27）
        ruleEditor?.let { request ->
            TxtRuleEditorOverlay(
                request = request,
                onDismiss = { ruleEditor = null },
                onDelete = if (request.id != null) {
                    {
                        applyRules(tocRules.filter { it.id != request.id })
                        ruleEditor = null
                    }
                } else {
                    null
                },
                onSave = { editedName, editedPattern ->
                    val updated = if (request.id == null) {
                        tocRules + TxtTocRule(System.currentTimeMillis(), editedName, editedPattern, true)
                    } else {
                        tocRules.map {
                            if (it.id == request.id) it.copy(name = editedName, pattern = editedPattern) else it
                        }
                    }
                    applyRules(updated)
                    ruleEditor = null
                },
            )
        }

        // 粘贴导入（fork.27）
        if (showPaste) {
            TxtPasteImportOverlay(
                onDismiss = { showPaste = false },
                onImport = { text ->
                    showPaste = false
                    importRulesFromText(text)
                },
            )
        }
    }
}

@Composable
private fun TxtRuleEditorOverlay(
    request: RuleEditRequest,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String, String) -> Unit,
) {
    var nameText by remember(request) { mutableStateOf(request.name) }
    var patternText by remember(request) { mutableStateOf(request.pattern) }
    var errorText by remember(request) { mutableStateOf<String?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable { onDismiss() },
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .clickable(enabled = false) {},
            insideMargin = PaddingValues(16.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = if (request.id == null) "新增规则" else "编辑规则",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "规则名称",
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = patternText,
                    onValueChange = {
                        patternText = it
                        errorText = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp),
                    label = "正则表达式（对每个标题行匹配）",
                    singleLine = false,
                )
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "保存后当前书的目录会按新规则重新生成。",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onDelete != null) {
                        TextButton(text = "删除", onClick = onDelete)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(text = "取消", onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        text = "保存",
                        onClick = {
                            val p = patternText.trim()
                            when {
                                nameText.isBlank() -> errorText = "规则名称不能为空"
                                p.isBlank() -> errorText = "正则表达式不能为空"
                                !TxtTocRules.isValidPattern(p) -> errorText = "正则表达式不合法，无法保存"
                                else -> onSave(nameText.trim(), p)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TxtPasteImportOverlay(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable { onDismiss() },
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .clickable(enabled = false) {},
            insideMargin = PaddingValues(16.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "粘贴导入",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    label = "Legado 规则 JSON，或一行一条：名称|正则（# 为注释）",
                    singleLine = false,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    TextButton(text = "取消", onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        text = "导入",
                        enabled = text.isNotBlank(),
                        onClick = { onImport(text) },
                    )
                }
            }
        }
    }
}

/* ---------------- 上下滚动正文（fork.26 形态，fork.27 抽出并挂位置上报） ---------------- */

@Composable
private fun TxtScrollContent(
    book: TxtBook,
    fontSize: Float,
    initialPara: Int,
    initialPx: Int,
    jumpPara: Int,
    jumpSignal: Int,
    tapToggleEnabled: Boolean,
    onToggleMenu: () -> Unit,
    onPosition: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(book) {
        if (!restored && book.paragraphs.isNotEmpty()) {
            restored = true
            listState.scrollToItem(
                initialPara.coerceIn(0, book.paragraphs.size - 1),
                initialPx,
            )
        }
    }
    LaunchedEffect(jumpSignal) {
        if (jumpSignal > 0 && jumpPara >= 0 && book.paragraphs.isNotEmpty()) {
            listState.scrollToItem(jumpPara.coerceIn(0, book.paragraphs.size - 1))
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                if (restored) onPosition(index, offset)
            }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier
            .fillMaxSize()
            .then(
                if (tapToggleEnabled) {
                    Modifier.pointerInput(Unit) { detectTapGestures { onToggleMenu() } }
                } else {
                    Modifier
                },
            ),
    ) {
        items(book.paragraphs.size) { index ->
            val isChapter = index in book.chapterStarts
            Text(
                text = book.paragraphs[index],
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

/* ---------------- 左右翻页正文（fork.27）：平移横滑动画 + 手势分区 ---------------- */

@Composable
private fun TxtPageContent(
    book: TxtBook,
    fontSize: Float,
    anchorPara: Int,
    anchorChar: Int,
    jumpPara: Int,
    jumpSignal: Int,
    onPosition: (Int, Int) -> Unit,
    onPageInfo: (Int, Int) -> Unit,
    onTapCenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val pages = remember(book, fontSize, widthPx, heightPx) {
            paginateTxt(
                measurer = measurer,
                paragraphs = book.paragraphs,
                chapterStarts = book.chapterStarts,
                fontSizeSp = fontSize,
                contentWidthPx = (widthPx - with(density) { 40.dp.toPx() }).toInt(),
                contentHeightPx = (heightPx - with(density) { 32.dp.toPx() }).toInt(),
                paragraphBottomPx = with(density) { 7.dp.toPx() },
                chapterTopPx = with(density) { 18.dp.toPx() },
            )
        }
        var pageIndex by remember { mutableIntStateOf(-1) }
        var lastPara by remember { mutableIntStateOf(anchorPara) }
        var lastChar by remember { mutableIntStateOf(anchorChar) }
        LaunchedEffect(pages) {
            if (pages.isNotEmpty()) {
                pageIndex = findTxtPage(pages, lastPara, lastChar)
            }
        }
        LaunchedEffect(jumpSignal) {
            if (jumpSignal > 0 && jumpPara >= 0 && pages.isNotEmpty()) {
                pageIndex = findTxtPage(pages, jumpPara, 0)
            }
        }
        LaunchedEffect(pageIndex, pages) {
            val p = pages.getOrNull(pageIndex) ?: return@LaunchedEffect
            lastPara = p.startParagraph
            lastChar = p.startChar
            onPosition(p.startParagraph, p.startChar)
            onPageInfo(pageIndex + 1, pages.size)
        }
        val anim = remember { Animatable(0f) }
        fun flip(delta: Int) {
            val target = pageIndex + delta
            if (target !in pages.indices || anim.isRunning) return
            scope.launch {
                anim.animateTo(if (delta > 0) -widthPx else widthPx, tween(230))
                pageIndex = target
                anim.snapTo(0f)
            }
        }
        val current = pages.getOrNull(pageIndex)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(pageIndex, pages.size) {
                    detectHorizontalDragGestures(
                        onDragStart = { scope.launch { anim.stop() } },
                        onDragEnd = {
                            val w = size.width.toFloat()
                            val v = anim.value
                            when {
                                v < -w * 0.22f && pageIndex < pages.size - 1 -> flip(1)
                                v > w * 0.22f && pageIndex > 0 -> flip(-1)
                                else -> scope.launch { anim.animateTo(0f, tween(200)) }
                            }
                        },
                        onDragCancel = { scope.launch { anim.animateTo(0f, tween(200)) } },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            scope.launch {
                                anim.snapTo((anim.value + amount).coerceIn(-size.width.toFloat(), size.width.toFloat()))
                            }
                        },
                    )
                }
                .pointerInput(pageIndex, pages.size) {
                    detectTapGestures { offset ->
                        if (anim.value != 0f) return@detectTapGestures
                        val w = size.width.toFloat()
                        when {
                            offset.x < w / 3f -> flip(-1)
                            offset.x > w * 2f / 3f -> flip(1)
                            else -> onTapCenter()
                        }
                    }
                },
        ) {
            if (current != null) {
                val off = anim.value
                TxtPageColumn(
                    page = current,
                    fontSize = fontSize,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(off.roundToInt(), 0) },
                )
                if (off < 0f) {
                    pages.getOrNull(pageIndex + 1)?.let { next ->
                        TxtPageColumn(
                            page = next,
                            fontSize = fontSize,
                            modifier = Modifier
                                .fillMaxSize()
                                .offset { IntOffset((off + widthPx).roundToInt(), 0) },
                        )
                    }
                }
                if (off > 0f) {
                    pages.getOrNull(pageIndex - 1)?.let { prev ->
                        TxtPageColumn(
                            page = prev,
                            fontSize = fontSize,
                            modifier = Modifier
                                .fillMaxSize()
                                .offset { IntOffset((off - widthPx).roundToInt(), 0) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TxtPageColumn(page: TxtPage, fontSize: Float, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        page.slices.forEachIndexed { idx, slice ->
            Text(
                text = slice.text,
                style = TextStyle(
                    fontSize = if (slice.chapter) (fontSize + 2).sp else fontSize.sp,
                    lineHeight = ((if (slice.chapter) fontSize + 2 else fontSize) * 1.75f).sp,
                    fontWeight = if (slice.chapter) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    top = if (slice.chapter && idx > 0) 18.dp else 0.dp,
                    bottom = 7.dp,
                ),
            )
        }
    }
}
