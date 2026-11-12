package dev.dsh.miuixbrowser

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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
import kotlinx.coroutines.delay
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
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberKyantBackdrop
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Locale
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
    fun paragraphsOf(text: String, reflow: Boolean = false): List<String> =
        if (reflow) {
            reflowParagraphs(text)
        } else {
            text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        }

    private val SENTENCE_END_CHARS =
        setOf('。', '！', '？', '…', '」', '』', '）', '】', '”', '’', '.', '!', '?')

    private val TITLE_PREFIXES =
        listOf("序章", "序言", "楔子", "前言", "引言", "尾声", "后记", "终章", "番外")

    /** 疑似章节标题行（重排时强制另起一段）：第X章式标题或特殊标题起头且不太长 */
    private fun looksLikeTitleLine(line: String): Boolean {
        if (line.length > 30) return false
        if (TITLE_PREFIXES.any { line.startsWith(it) }) return true
        if (line.startsWith("第")) {
            val head = line.take(12)
            return head.any { it in "章回节卷部篇集" }
        }
        return false
    }

    /**
     * 文本重排（fork.30）：治硬换行排版乱的 txt——把被换行拆断的同一段
     * 合并回一段。口径：空行必断段；原行带缩进（全角空格 / 两个以上 ASCII
     * 空格 / Tab 起头）必起新段；疑似章节标题行必起新段；上一行以句末标点
     * 结尾则断段；其余情况与上一行直接拼接（中文正文拼接不加分隔符）。
     * 连续空行自然被吃掉。
     */
    private fun reflowParagraphs(text: String): List<String> {
        val out = ArrayList<String>()
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) {
                out += buf.toString()
                buf.setLength(0)
            }
        }
        for (raw in text.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty()) {
                flush()
                continue
            }
            if (buf.isEmpty()) {
                buf.append(line)
                continue
            }
            val startsNew = raw.startsWith('　') || raw.startsWith("  ") ||
                raw.startsWith('\t') || looksLikeTitleLine(line)
            if (startsNew || buf[buf.length - 1] in SENTENCE_END_CHARS) {
                flush()
                buf.append(line)
            } else {
                buf.append(line)
            }
        }
        flush()
        return out
    }
}

/**
 * 简繁转换（fork.30）：用系统 android.icu.text.Transliterator 的
 * Traditional-Simplified / Simplified-Traditional 规则（ICU 系统数据，
 * 覆盖常用与非常用汉字转换表，无需自带词典）。在解码后、切章前对全文
 * 作用一次，目录 / 搜索 / 进度锚点都建立在转换后文本上、口径自洽。
 * 转换失败（规则不可用等）静默回退原文。
 */
object TxtConvert {

    const val OFF = "off"
    const val T2S = "t2s"
    const val S2T = "s2t"

    fun convert(text: String, mode: String): String {
        if (mode == OFF || text.isEmpty()) return text
        val id = if (mode == T2S) "Traditional-Simplified" else "Simplified-Traditional"
        return runCatching {
            val transliterator = android.icu.text.Transliterator.getInstance(id)
            // 分块转换，避免整本大书在 ICU 内部一次性处理时内存抖动；
            // 按行切块、块边界落在换行后，简繁映射逐字进行、不受块界影响
            val out = StringBuilder(text.length)
            var chunk = StringBuilder()
            for (line in text.split('\n')) {
                chunk.append(line).append('\n')
                if (chunk.length >= 32_768) {
                    out.append(transliterator.transliterate(chunk.toString()))
                    chunk = StringBuilder()
                }
            }
            if (chunk.isNotEmpty()) out.append(transliterator.transliterate(chunk.toString()))
            // split/join 往返会在文末多出一个换行，去掉以与原文形态一致
            out.toString().removeSuffix("\n")
        }.getOrNull() ?: text
    }
}

/**
 * 自定义字体（fork.30）：从系统文件选择器导入 ttf/otf，复制进应用
 * 私有目录 files/txt_fonts 后长期持有；列表管理与删除都在这里。
 * 渲染与翻页测量共用同一 Typeface（按字重烘焙，见 TxtFeatureState 的
 * 字体解析），保证分页量高与实际排版一致。
 */
object TxtFonts {

    data class Entry(val fileName: String, val label: String)

    private fun dir(context: Context): File =
        File(context.filesDir, "txt_fonts").apply { mkdirs() }

    fun list(context: Context): List<Entry> = runCatching {
        dir(context).listFiles()
            ?.filter { it.isFile && (it.extension.equals("ttf", true) || it.extension.equals("otf", true)) }
            ?.sortedBy { it.name.lowercase() }
            ?.map { Entry(it.name, it.nameWithoutExtension) }
            ?: emptyList()
    }.getOrDefault(emptyList())

    /** 导入字体文件到私有目录，返回落盘文件名；同名覆盖 */
    fun importFont(context: Context, uri: Uri): String? = runCatching {
        val display = queryDisplayName(context, uri) ?: "font-${System.currentTimeMillis()}.ttf"
        val safe = display.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .takeIf { it.endsWith(".ttf", true) || it.endsWith(".otf", true) }
            ?: "$display.ttf"
        val target = File(dir(context), safe)
        val stream = if (uri.scheme == ContentResolver.SCHEME_FILE) {
            File(uri.path ?: return null).inputStream()
        } else {
            context.contentResolver.openInputStream(uri) ?: return null
        }
        stream.use { input -> target.outputStream().use { input.copyTo(it) } }
        // 校验能被系统解析成字体（坏文件 createFromFile 会抛异常），坏文件不留
        val parsed = runCatching { android.graphics.Typeface.createFromFile(target) }.getOrNull()
        if (parsed == null) {
            target.delete()
            return null
        }
        target.name
    }.getOrNull()

    fun delete(context: Context, fileName: String) {
        runCatching { File(dir(context), fileName).delete() }
        baseCache.remove(fileName)
    }

    private val baseCache = HashMap<String, android.graphics.Typeface>()

    /** 按文件取基础 Typeface（缓存）；[weight] 经 Typeface.create 烘焙进字重 */
    fun typeface(context: Context, fileName: String, weight: Int): android.graphics.Typeface? {
        val base = baseCache.getOrPut(fileName) {
            runCatching { android.graphics.Typeface.createFromFile(File(dir(context), fileName)) }
                .getOrNull() ?: return null
        }
        return runCatching {
            android.graphics.Typeface.create(base, weight.coerceIn(100, 1000), false)
        }.getOrDefault(base)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) return cursor.getString(idx)?.takeIf { it.isNotBlank() }
                        }
                    }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }
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
        markProgressTs(context, space, key)
    }

    private const val KEY_BRIGHTNESS = "brightness"

    /** 阅读窗口亮度偏好：0..1，-1 = 跟随系统（不覆盖窗口亮度） */
    fun brightness(context: Context): Float = runCatching {
        uiPrefs(context).getFloat(KEY_BRIGHTNESS, -1f)
    }.getOrDefault(-1f)

    fun setBrightness(context: Context, value: Float) {
        runCatching { uiPrefs(context).edit().putFloat(KEY_BRIGHTNESS, value).apply() }
    }

    /**
     * 本书选用的目录规则 id（fork.28）：与进度同口径按空间分开存——
     * 隐私空间的书选了哪条规则不落普通空间。无记录 = 「自动」，
     * 沿用全局规则列表顺序切章。
     */
    fun loadTocRuleChoice(context: Context, space: Int, key: String): Long? =
        runCatching {
            val prefs = progressPrefs(context, space)
            if (prefs.contains("r_$key")) prefs.getLong("r_$key", 0L) else null
        }.getOrNull()

    fun saveTocRuleChoice(context: Context, space: Int, key: String, ruleId: Long?) {
        runCatching {
            val editor = progressPrefs(context, space).edit()
            if (ruleId == null) editor.remove("r_$key") else editor.putLong("r_$key", ruleId)
            editor.apply()
        }
        markTocTs(context, space, key)
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

    /** 全屏阅读开关：默认开（隐藏应用顶/底栏、正文全幅，系统栏保持显示），可关，选择持久化 */
    fun fullscreen(context: Context): Boolean = runCatching {
        uiPrefs(context).getBoolean(KEY_FULLSCREEN, true)
    }.getOrDefault(true)

    fun setFullscreen(context: Context, value: Boolean) {
        runCatching { uiPrefs(context).edit().putBoolean(KEY_FULLSCREEN, value).apply() }
    }

    private const val KEY_LINE_HEIGHT = "line_height"
    private const val KEY_PARA_SPACING = "para_spacing_dp"
    private const val KEY_MARGIN = "margin_dp"
    private const val KEY_BG_PRESET = "bg_preset"

    const val BG_THEME = "theme"
    const val BG_CREAM = "cream"
    const val BG_GREEN = "green"
    const val BG_NIGHT = "night"

    const val DEFAULT_LINE_HEIGHT = 1.75f
    const val DEFAULT_PARA_SPACING_DP = 7f
    const val DEFAULT_MARGIN_DP = 20f

    /** 行距倍数（fork.29 排版）：滚动与翻页共用，分页时按它量高 */
    fun lineHeight(context: Context): Float = runCatching {
        uiPrefs(context).getFloat(KEY_LINE_HEIGHT, DEFAULT_LINE_HEIGHT)
    }.getOrDefault(DEFAULT_LINE_HEIGHT)

    fun setLineHeight(context: Context, value: Float) {
        runCatching { uiPrefs(context).edit().putFloat(KEY_LINE_HEIGHT, value).apply() }
    }

    /** 段间距 dp（fork.29 排版）：段落之间的纵向间隔 */
    fun paraSpacing(context: Context): Float = runCatching {
        uiPrefs(context).getFloat(KEY_PARA_SPACING, DEFAULT_PARA_SPACING_DP)
    }.getOrDefault(DEFAULT_PARA_SPACING_DP)

    fun setParaSpacing(context: Context, value: Float) {
        runCatching { uiPrefs(context).edit().putFloat(KEY_PARA_SPACING, value).apply() }
    }

    /** 正文左右边距 dp（fork.29 排版） */
    fun margin(context: Context): Float = runCatching {
        uiPrefs(context).getFloat(KEY_MARGIN, DEFAULT_MARGIN_DP)
    }.getOrDefault(DEFAULT_MARGIN_DP)

    fun setMargin(context: Context, value: Float) {
        runCatching { uiPrefs(context).edit().putFloat(KEY_MARGIN, value).apply() }
    }

    /** 阅读页背景预设（fork.29）：theme 跟随主题，其余为固定底色，仅作用于阅读页 */
    fun bgPreset(context: Context): String = runCatching {
        uiPrefs(context).getString(KEY_BG_PRESET, BG_THEME) ?: BG_THEME
    }.getOrDefault(BG_THEME)

    fun setBgPreset(context: Context, value: String) {
        runCatching { uiPrefs(context).edit().putString(KEY_BG_PRESET, value).apply() }
    }

    /* ---------------- fork.30 新增设置与数据 ---------------- */

    private const val KEY_CONVERT = "convert_mode"
    private const val KEY_REFLOW = "reflow"
    private const val KEY_FONT_FILE = "font_file"
    private const val KEY_FONT_WEIGHT = "font_weight"
    private const val KEY_INDENT = "indent_chars"
    private const val KEY_JUSTIFY = "justify"
    private const val KEY_PAGE_ANIM = "page_anim"
    private const val KEY_TOC_REVERSED = "toc_reversed"
    private const val KEY_FOOTER_CHAPTER = "footer_chapter"
    private const val KEY_FOOTER_PAGE = "footer_page"
    private const val KEY_FOOTER_PERCENT = "footer_percent"
    private const val KEY_FOOTER_TIME = "footer_time"
    private const val KEY_FOOTER_BATTERY = "footer_battery"
    private const val KEY_SHELF_SORT = "shelf_sort"

    const val ANIM_SLIDE = "slide"
    const val ANIM_COVER = "cover"
    const val ANIM_VERTICAL = "vertical"
    const val ANIM_CURL = "curl"
    const val ANIM_NONE = "none"

    const val DEFAULT_FONT_WEIGHT = 400

    /** 简繁转换模式：TxtConvert.OFF / T2S / S2T，全局一份 */
    fun convertMode(context: Context): String = runCatching {
        uiPrefs(context).getString(KEY_CONVERT, TxtConvert.OFF) ?: TxtConvert.OFF
    }.getOrDefault(TxtConvert.OFF)

    fun setConvertMode(context: Context, value: String) {
        runCatching { uiPrefs(context).edit().putString(KEY_CONVERT, value).apply() }
    }

    /** 文本重排开关（默认关） */
    fun reflow(context: Context): Boolean = runCatching {
        uiPrefs(context).getBoolean(KEY_REFLOW, false)
    }.getOrDefault(false)

    fun setReflow(context: Context, value: Boolean) {
        runCatching { uiPrefs(context).edit().putBoolean(KEY_REFLOW, value).apply() }
    }

    /** 自定义字体文件名（files/txt_fonts 下），空 = 系统默认字体 */
    fun fontFile(context: Context): String = runCatching {
        uiPrefs(context).getString(KEY_FONT_FILE, "") ?: ""
    }.getOrDefault("")

    fun setFontFile(context: Context, value: String) {
        runCatching { uiPrefs(context).edit().putString(KEY_FONT_FILE, value).apply() }
    }

    /** 字重 100..900（默认 400） */
    fun fontWeight(context: Context): Int = runCatching {
        uiPrefs(context).getInt(KEY_FONT_WEIGHT, DEFAULT_FONT_WEIGHT)
    }.getOrDefault(DEFAULT_FONT_WEIGHT)

    fun setFontWeight(context: Context, value: Int) {
        runCatching { uiPrefs(context).edit().putInt(KEY_FONT_WEIGHT, value).apply() }
    }

    /** 首行缩进字数：0 / 1 / 2（全角空格宽） */
    fun indentChars(context: Context): Int = runCatching {
        uiPrefs(context).getInt(KEY_INDENT, 0)
    }.getOrDefault(0)

    fun setIndentChars(context: Context, value: Int) {
        runCatching { uiPrefs(context).edit().putInt(KEY_INDENT, value).apply() }
    }

    /** 两端对齐开关（关 = 左对齐） */
    fun justify(context: Context): Boolean = runCatching {
        uiPrefs(context).getBoolean(KEY_JUSTIFY, false)
    }.getOrDefault(false)

    fun setJustify(context: Context, value: Boolean) {
        runCatching { uiPrefs(context).edit().putBoolean(KEY_JUSTIFY, value).apply() }
    }

    /** 翻页动画：ANIM_* 之一 */
    fun pageAnim(context: Context): String = runCatching {
        uiPrefs(context).getString(KEY_PAGE_ANIM, ANIM_SLIDE) ?: ANIM_SLIDE
    }.getOrDefault(ANIM_SLIDE)

    fun setPageAnim(context: Context, value: String) {
        runCatching { uiPrefs(context).edit().putString(KEY_PAGE_ANIM, value).apply() }
    }

    /** 目录倒序显示偏好 */
    fun tocReversed(context: Context): Boolean = runCatching {
        uiPrefs(context).getBoolean(KEY_TOC_REVERSED, false)
    }.getOrDefault(false)

    fun setTocReversed(context: Context, value: Boolean) {
        runCatching { uiPrefs(context).edit().putBoolean(KEY_TOC_REVERSED, value).apply() }
    }

    /** 底部信息行元素开关：章节名默认开、页码/百分比默认开、时间/电量默认关 */
    fun footerShow(context: Context, key: String): Boolean {
        val (prefKey, default) = when (key) {
            "chapter" -> KEY_FOOTER_CHAPTER to true
            "page" -> KEY_FOOTER_PAGE to true
            "percent" -> KEY_FOOTER_PERCENT to true
            "time" -> KEY_FOOTER_TIME to false
            "battery" -> KEY_FOOTER_BATTERY to false
            else -> return true
        }
        return runCatching { uiPrefs(context).getBoolean(prefKey, default) }.getOrDefault(default)
    }

    fun setFooterShow(context: Context, key: String, value: Boolean) {
        val prefKey = when (key) {
            "chapter" -> KEY_FOOTER_CHAPTER
            "page" -> KEY_FOOTER_PAGE
            "percent" -> KEY_FOOTER_PERCENT
            "time" -> KEY_FOOTER_TIME
            "battery" -> KEY_FOOTER_BATTERY
            else -> return
        }
        runCatching { uiPrefs(context).edit().putBoolean(prefKey, value).apply() }
    }

    /** 书架排序口径：recent / name / added */
    fun shelfSort(context: Context): String = runCatching {
        uiPrefs(context).getString(KEY_SHELF_SORT, "recent") ?: "recent"
    }.getOrDefault("recent")

    fun setShelfSort(context: Context, value: String) {
        runCatching { uiPrefs(context).edit().putString(KEY_SHELF_SORT, value).apply() }
    }

    /**
     * 每本书累计阅读秒数（fork.30 统计）：与进度同文件、同空间口径，
     * 键 t_<文件标识>。阅读器只在前台停留期间累计（进出/暂停结算）。
     */
    fun readSeconds(context: Context, space: Int, key: String): Long = runCatching {
        progressPrefs(context, space).getLong("t_$key", 0L)
    }.getOrDefault(0L)

    fun addReadSeconds(context: Context, space: Int, key: String, seconds: Long) {
        if (seconds <= 0) return
        runCatching {
            val prefs = progressPrefs(context, space)
            prefs.edit().putLong("t_$key", prefs.getLong("t_$key", 0L) + seconds).apply()
        }
    }

    /* -------- WebDAV 同步用的记录时间戳（fork.30）：独立小 prefs -------- */

    private fun syncMetaPrefs(context: Context) =
        context.getSharedPreferences("marea_txt_sync_meta", Context.MODE_PRIVATE)

    fun markProgressTs(context: Context, space: Int, key: String, ts: Long = System.currentTimeMillis()) {
        runCatching { syncMetaPrefs(context).edit().putLong("pt_${space}_$key", ts).apply() }
    }

    fun markTocTs(context: Context, space: Int, key: String, ts: Long = System.currentTimeMillis()) {
        runCatching { syncMetaPrefs(context).edit().putLong("tt_${space}_$key", ts).apply() }
    }

    fun progressTs(context: Context, space: Int, key: String): Long = runCatching {
        syncMetaPrefs(context).getLong("pt_${space}_$key", 0L)
    }.getOrDefault(0L)

    fun tocTs(context: Context, space: Int, key: String): Long = runCatching {
        syncMetaPrefs(context).getLong("tt_${space}_$key", 0L)
    }.getOrDefault(0L)

    /** 枚举某空间下全部进度键（同步快照用）：返回文件标识列表 */
    fun progressKeys(context: Context, space: Int): List<String> = runCatching {
        progressPrefs(context, space).all.keys
            .filter { it.startsWith("p_") }
            .map { it.removePrefix("p_") }
    }.getOrDefault(emptyList())

    /** 枚举某空间下全部「本书目录规则选择」键（同步快照用） */
    fun tocChoiceKeys(context: Context, space: Int): List<String> = runCatching {
        progressPrefs(context, space).all.keys
            .filter { it.startsWith("r_") }
            .map { it.removePrefix("r_") }
    }.getOrDefault(emptyList())

    /** 同步合入远端进度时用：写进度并把记录时间戳钉为远端时间（不冒充本地新改动） */
    fun saveProgressFromSync(
        context: Context,
        space: Int,
        key: String,
        paragraph: Int,
        offsetPx: Int,
        charOffset: Int,
        encoding: String?,
        total: Int,
        ts: Long,
    ) {
        saveProgress(context, space, key, paragraph, offsetPx, charOffset, encoding, total)
        markProgressTs(context, space, key, ts)
    }

    /** 同步合入远端规则选择时用：写选择并把时间戳钉为远端时间 */
    fun saveTocRuleChoiceFromSync(context: Context, space: Int, key: String, ruleId: Long?, ts: Long) {
        saveTocRuleChoice(context, space, key, ruleId)
        markTocTs(context, space, key, ts)
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
        // fork.28：全幅铺满——窗口不避让系统栏，状态栏/导航条透明常显，
        // 内容侧用 windowInsetsPadding 给系统栏让位（与 MainActivity 同口径）
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // 真沉浸（fork.29）：禁止系统在透明系统栏下再垫一层对比度蒙层，
        // 阅读页背景要原样延伸到状态栏 / 导航条之下
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
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

class TxtBook(
    val paragraphs: List<String>,
    val chapters: List<TxtChapter>,
    val chapterStarts: Set<Int>,
    val encodingLabel: String,
)

/**
 * 阅读页配色（fork.29 背景预设）：底色 / 正文字色 / 次级文字色 / 页面是否深色
 * （决定系统栏图标明暗）。预设只作用于阅读页，不动全应用主题。
 */
internal class TxtReaderPalette(
    val bg: Color,
    val text: Color,
    val sub: Color,
    val dark: Boolean,
)

/** 背景预设的固定底色；跟随主题返回 null（由调用方用主题 surface） */
internal fun txtReaderBgColor(preset: String): Color? = when (preset) {
    TxtReaderStore.BG_CREAM -> Color(0xFFF4ECDC)
    TxtReaderStore.BG_GREEN -> Color(0xFFD3ECCB)
    TxtReaderStore.BG_NIGHT -> Color(0xFF242424)
    else -> null
}

@Composable
internal fun rememberTxtReaderPalette(preset: String): TxtReaderPalette = when (preset) {
    TxtReaderStore.BG_CREAM -> TxtReaderPalette(
        bg = Color(0xFFF4ECDC),
        text = Color(0xFF38312A),
        sub = Color(0xFF38312A).copy(alpha = 0.55f),
        dark = false,
    )
    TxtReaderStore.BG_GREEN -> TxtReaderPalette(
        bg = Color(0xFFD3ECCB),
        text = Color(0xFF26371F),
        sub = Color(0xFF26371F).copy(alpha = 0.55f),
        dark = false,
    )
    TxtReaderStore.BG_NIGHT -> TxtReaderPalette(
        bg = Color(0xFF242424),
        text = Color(0xFFD9D9D9),
        sub = Color(0xFFD9D9D9).copy(alpha = 0.55f),
        dark = true,
    )
    else -> {
        val scheme = MiuixTheme.colorScheme
        TxtReaderPalette(
            bg = scheme.surface,
            text = scheme.onSurface,
            sub = scheme.onSurfaceVariantSummary,
            dark = scheme.surface.luminance() < 0.5f,
        )
    }
}

/**
 * 生效中的字体规格（fork.30）：正文渲染与翻页 TextMeasurer 量测必须用
 * 同一个 [family] + [weight]，否则分页与排版必然错位——所有正文入口
 * 都只从这里取。自定义字体的字重已烘焙进 Typeface（Typeface.create 的
 * weight 参数），故 weight 归 Normal；系统默认字体走 Compose 的
 * FontWeight 合成，两条路都在量测与渲染间同口径。
 */
internal class TxtFontSpec(
    val family: androidx.compose.ui.text.font.FontFamily,
    val weight: FontWeight,
)

@Composable
internal fun rememberTxtFontSpec(fontFile: String, fontWeightValue: Int): TxtFontSpec {
    val context = LocalContext.current
    return remember(fontFile, fontWeightValue, context) {
        if (fontFile.isNotBlank()) {
            val typeface = TxtFonts.typeface(context, fontFile, fontWeightValue)
            if (typeface != null) {
                return@remember TxtFontSpec(
                    androidx.compose.ui.text.font.FontFamily(typeface),
                    FontWeight.Normal,
                )
            }
        }
        TxtFontSpec(
            androidx.compose.ui.text.font.FontFamily.Default,
            FontWeight(fontWeightValue.coerceIn(100, 900)),
        )
    }
}

/** 当前电量百分比（fork.30 页脚元素）：读 ACTION_BATTERY_CHANGED 的 sticky 意图 */
private fun txtBatteryPercent(context: Context): Int? = runCatching {
    val intent = context.registerReceiver(
        null,
        android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED),
    ) ?: return null
    val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) null else level * 100 / scale
}.getOrNull()

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

/** 翻页模式上报的位置信息（fork.28）：全局页码 + 本章页码（无章节时后两者为 0） */
private data class TxtPageInfo(
    val current: Int,
    val total: Int,
    val chapterCurrent: Int,
    val chapterTotal: Int,
)

/** 一段文本量出的逐行信息：行首/行末字符偏移（原段坐标）与行高 */
private class TxtParaLines(
    val starts: IntArray,
    val ends: IntArray,
    val heights: FloatArray,
) {
    val count: Int get() = starts.size
}

/**
 * 量一段文本的逐行断行。首行缩进（fork.30）的处理：缩进是呈现层的事，
 * 不改文本——首行单独按「内容宽 − 缩进宽」量、余下文字按全宽量，
 * 两次量测的行首/行末都换算回原段字符坐标。这样（段落, 字符偏移）
 * 锚点与进度口径完全不受缩进影响，渲染时只在段首切片前补全角空格。
 */
private fun measureTxtParaLines(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    style: TextStyle,
    widthPx: Int,
    indentPx: Int,
): TxtParaLines {
    fun linesOf(layout: androidx.compose.ui.text.TextLayoutResult, base: Int): TxtParaLines {
        val n = layout.lineCount
        return TxtParaLines(
            IntArray(n) { base + layout.getLineStart(it) },
            IntArray(n) { base + layout.getLineEnd(it, true) },
            FloatArray(n) { layout.getLineBottom(it) - layout.getLineTop(it) },
        )
    }
    if (indentPx <= 0 || text.isEmpty()) {
        return linesOf(
            measurer.measure(text, style, Constraints(maxWidth = widthPx)),
            0,
        )
    }
    val headLayout = measurer.measure(
        text, style, Constraints(maxWidth = (widthPx - indentPx).coerceAtLeast(1)),
    )
    val firstEnd = headLayout.getLineEnd(0, true)
    if (firstEnd <= 0 || firstEnd >= text.length) {
        return if (firstEnd <= 0) {
            linesOf(measurer.measure(text, style, Constraints(maxWidth = widthPx)), 0)
        } else {
            TxtParaLines(
                intArrayOf(0),
                intArrayOf(firstEnd),
                floatArrayOf(headLayout.getLineBottom(0) - headLayout.getLineTop(0)),
            )
        }
    }
    val firstHeight = headLayout.getLineBottom(0) - headLayout.getLineTop(0)
    val rest = linesOf(
        measurer.measure(text.substring(firstEnd), style, Constraints(maxWidth = widthPx)),
        firstEnd,
    )
    return TxtParaLines(
        intArrayOf(0) + rest.starts,
        intArrayOf(firstEnd) + rest.ends,
        floatArrayOf(firstHeight) + rest.heights,
    )
}

/**
 * 用 TextMeasurer 按当前字号/行高逐段逐行量高，贪心装页；段落可跨页拆分，
 * 拆分点用行首字符索引表达，保证页与段落/字符偏移互相可定位。
 * 段间距与滚动模式同口径（fork.29 排版设置）：段后为段间距、章节段前
 * 为段间距×2+4dp（页首章节不加前距）；行距倍数同样来自排版设置。
 * fork.30 起字体 / 字重 / 对齐与首行缩进一并进入量测，量测样式与渲染
 * 样式严格同一份，否则分页必与实际排版错位。
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
    lineHeightFactor: Float,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    fontWeight: FontWeight,
    indentChars: Int,
    justify: Boolean,
): List<TxtPage> {
    if (paragraphs.isEmpty() || contentWidthPx <= 0 || contentHeightPx <= 0) {
        return listOf(TxtPage(emptyList()))
    }
    val align = if (justify) {
        androidx.compose.ui.text.style.TextAlign.Justify
    } else {
        androidx.compose.ui.text.style.TextAlign.Left
    }
    val bodyStyle = TextStyle(
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * lineHeightFactor).sp,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        textAlign = align,
    )
    // 缩进宽度按正文样式实测（全角空格 × 缩进字数），不拍脑袋估
    val indentPx = if (indentChars > 0) {
        measurer.measure("　".repeat(indentChars), bodyStyle).size.width
    } else {
        0
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
        val style = if (chapter) {
            TextStyle(
                fontSize = size.sp,
                lineHeight = (size * lineHeightFactor).sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.SemiBold,
                textAlign = align,
            )
        } else {
            bodyStyle
        }
        val lines = measureTxtParaLines(
            measurer, paragraphs[i], style, contentWidthPx,
            if (chapter) 0 else indentPx,
        )
        val totalLines = lines.count
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
                val lh = lines.heights[line + take]
                if (take > 0 && h + lh > avail) break
                h += lh
                take++
                if (h > avail) break // 单行比整页还高：强制放一行，避免死循环
            }
            if (take == 0) {
                flushPage()
                continue
            }
            val startChar = lines.starts[line]
            val endChar = lines.ends[line + take - 1]
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
    var panelVisible by remember { mutableStateOf(false) }
    var ruleChoice by remember { mutableStateOf<Long?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf(TxtReaderStore.brightness(context)) }
    val systemBrightness = remember {
        runCatching {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS, 128,
            ) / 255f
        }.getOrDefault(0.55f)
    }

    // 统一位置锚点：段落 + 段内字符偏移，两种阅读模式共用（见 TxtReaderStore 注释）
    var anchorPara by remember { mutableIntStateOf(0) }
    var anchorChar by remember { mutableIntStateOf(0) }
    var scrollPx by remember { mutableIntStateOf(0) }
    var positionReady by remember { mutableStateOf(false) }
    var jumpPara by remember { mutableIntStateOf(-1) }
    var jumpSignal by remember { mutableIntStateOf(0) }
    var pageInfo by remember { mutableStateOf<TxtPageInfo?>(null) }

    var showSettings by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var lineHeight by remember { mutableStateOf(TxtReaderStore.lineHeight(context)) }
    var paraSpacing by remember { mutableStateOf(TxtReaderStore.paraSpacing(context)) }
    var marginDp by remember { mutableStateOf(TxtReaderStore.margin(context)) }
    var readerBg by remember { mutableStateOf(TxtReaderStore.bgPreset(context)) }
    val settingsNav = remember { TxtReaderSettingsNav() }
    // fork.30 功能批的统一状态（与设置组件共用同一份，写同一处存储）
    val features = remember { TxtFeatureState(appContext) }
    val fontSpec = rememberTxtFontSpec(features.fontFile, features.fontWeight)
    var selectionPara by remember { mutableStateOf<Int?>(null) }
    var searchInitial by remember { mutableStateOf("") }
    var tocQuery by remember { mutableStateOf("") }

    fun applyRules(newRules: List<TxtTocRule>) {
        tocRules = newRules
        TxtTocRules.save(appContext, newRules)
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
                ruleChoice = TxtReaderStore.loadTocRuleChoice(appContext, space, key)
                // 打开过即入架（fork.27 书架），空间与进度同口径分开
                TxtBookshelfStore.upsert(appContext, space, key, uri.toString(), name, bytes.size)
                // WebDAV 静默同步（fork.30）：配过服务器才会真正跑，失败静默
                scope.launch { TxtSync.syncIfConfigured(appContext) }
                Diag.log("TxtReader: loaded ${bytes.size} bytes, progress=${progress != null}")
            }
            .onFailure {
                Diag.log("TxtReader: read failed: ${it.message}")
                loadError = true
            }
    }

    // 解码 + 按当前规则切章（编码或规则变化时重跑）
    LaunchedEffect(rawBytes, encodingChoice, tocRules, ruleChoice, features.convertMode, features.reflow) {
        val bytes = rawBytes ?: return@LaunchedEffect
        val decoded = withContext(Dispatchers.Default) {
            TxtCodec.decode(bytes, encodingChoice)
        }
        // fork.30：简繁转换在解码后、切章前作用于全文（目录 / 搜索 / 进度
        // 锚点都建立在转换后文本上）；文本重排在分段时完成，同在切章之前
        val paragraphs = withContext(Dispatchers.Default) {
            val converted = TxtConvert.convert(decoded.text, features.convertMode)
            TxtCodec.paragraphsOf(converted, features.reflow)
        }
        // 本书指定了单条规则时只用它切章（本书专用，忽略其全局启停）；
        // 未指定（自动）沿用全局规则列表顺序的现行口径
        val chosenRule = ruleChoice?.let { id -> tocRules.firstOrNull { it.id == id } }
        val chapters = TxtChapters.split(
            paragraphs,
            if (chosenRule != null) listOf(chosenRule.copy(enabled = true)) else tocRules,
        )
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

    // 阅读时长统计（fork.30）：只计阅读器 Activity 处于 RESUMED 的时间，
    // ON_PAUSE / 销毁时结算进本书累计秒数——挂后台、跳系统界面都不计入
    val readerActivity = context as? ComponentActivity
    DisposableEffect(readerActivity, fileKey) {
        val lifecycle = readerActivity?.lifecycle
        val keySnapshot = fileKey
        var segmentStart = 0L
        fun settle() {
            if (segmentStart > 0L) {
                val secs = (android.os.SystemClock.elapsedRealtime() - segmentStart) / 1000
                segmentStart = 0L
                if (keySnapshot != null && secs > 0) {
                    TxtReaderStore.addReadSeconds(appContext, space, keySnapshot, secs)
                }
            }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME ->
                    segmentStart = android.os.SystemClock.elapsedRealtime()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> settle()
                else -> Unit
            }
        }
        lifecycle?.addObserver(observer)
        if (lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true) {
            segmentStart = android.os.SystemClock.elapsedRealtime()
        }
        onDispose {
            settle()
            lifecycle?.removeObserver(observer)
        }
    }

    // fork.28 全屏口径纠正：系统状态栏与导航条全程保持显示，阅读器从不
    // 隐藏它们；「全屏」只表示隐藏应用自己的顶/底栏、正文全幅铺满，
    // 内容靠 windowInsetsPadding 给系统栏让位（见下方布局）。

    // 阅读亮度偏好 → 当前窗口亮度（-1 = 跟随系统，恢复不覆盖）
    LaunchedEffect(brightness) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val attrs = window.attributes
        attrs.screenBrightness = if (brightness >= 0f) {
            brightness
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = attrs
    }

    val currentBook = book
    val total = currentBook?.paragraphs?.size ?: 0
    val chaptersNow = currentBook?.chapters.orEmpty()
    val chapterIndex = chaptersNow.indexOfLast { it.paragraphIndex <= anchorPara }
    val currentChapter = chaptersNow.getOrNull(chapterIndex)?.title
    val chapterStartPara = chaptersNow.getOrNull(chapterIndex)?.paragraphIndex ?: 0
    val chapterEndPara = chaptersNow.getOrNull(chapterIndex + 1)?.paragraphIndex ?: total
    val percentText = String.format(
        Locale.US, "%.1f%%",
        if (total > 0) anchorPara * 100f / total else 0f,
    )
    // 页脚时间（fork.30 页眉页脚自定义）：开了时间元素才需要时钟，每 15 秒走一格
    var clockMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            clockMs = System.currentTimeMillis()
        }
    }
    val batteryPct = remember(clockMs) { txtBatteryPercent(appContext) }
    // 底部信息行右侧（Legado 式 + fork.30 元素开关）：关掉的元素不出现、
    // 不占位；翻页模式页码 = 本章第 x/y 页（无章节时全书页码）
    val infoRight = if (currentBook == null) {
        ""
    } else {
        buildList {
            if (features.footerPage && readMode == TxtReaderStore.MODE_PAGE && pageInfo != null) {
                add(
                    if (pageInfo!!.chapterTotal > 0) {
                        "${pageInfo!!.chapterCurrent}/${pageInfo!!.chapterTotal}"
                    } else {
                        "${pageInfo!!.current}/${pageInfo!!.total}"
                    },
                )
            }
            if (features.footerPercent) add(percentText)
            if (features.footerTime) {
                add(
                    java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(java.util.Date(clockMs)),
                )
            }
            if (features.footerBattery && batteryPct != null) add("$batteryPct%")
        }.joinToString("  ")
    }

    // 阅读器玻璃（fork.29）：与浏览器同款接法——正文录进双采样层（MIUIX 玻璃与
    // Kyant 液态玻璃各一份，录制前先垫阅读页底色），浮层经 glassBar 取样；
    // 材质模式跟随应用全局 LocalBlurMode（关 / MIUIX / 液态），不自成一套开关
    val palette = rememberTxtReaderPalette(readerBg)
    val readerBgState = rememberUpdatedState(palette.bg)
    val readerBackdrop = rememberLayerBackdrop {
        drawRect(readerBgState.value)
        drawContent()
    }
    val readerLiquidBackdrop = rememberKyantBackdrop {
        drawRect(readerBgState.value)
        drawContent()
    }
    val blurMode = LocalBlurMode.current
    val glassBlurEnabled = blurMode != BLUR_OFF
    val liquidBackdrop: com.kyant.backdrop.Backdrop? =
        if (blurMode == BLUR_LIQUID) readerLiquidBackdrop else null

    val topBar: @Composable () -> Unit = {
        SmallTopAppBar(
            title = name,
            color = palette.bg,
            titleColor = palette.text,
            subtitleColor = palette.sub,
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

    // 全屏呼出的顶栏（Legado 式）：只留返回 + 当前章节标题，不堆按钮
    val slimTopBar: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassBar(
                    backdrop = readerBackdrop,
                    corner = 0.dp,
                    blurEnabled = glassBlurEnabled,
                    liquid = liquidBackdrop,
                )
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidGlassBackButton(backdrop = null, surfaceVisible = true, onClick = onClose)
            Text(
                text = currentChapter ?: name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }

    // 普通模式底栏：目录/设置入口与 Legado 底部信息行并成一行，不重复占高
    val bottomBar: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassBar(
                    backdrop = readerBackdrop,
                    corner = 0.dp,
                    blurEnabled = glassBlurEnabled,
                    liquid = liquidBackdrop,
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                text = "目录",
                onClick = { showToc = true },
                enabled = chaptersNow.isNotEmpty(),
            )
            Text(
                text = if (features.footerChapter) currentChapter ?: "" else "",
                fontSize = 12.sp,
                color = palette.sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            )
            Text(
                text = infoRight,
                fontSize = 12.sp,
                color = palette.sub,
            )
            TextButton(
                text = "设置",
                onClick = {
                    settingsNav.section = null
                    showSettings = true
                },
                enabled = currentBook != null,
            )
        }
    }

    // 全屏模式底部信息行（Legado 式）：左当前章节名，右本章页码 + 全书百分比
    val infoRow: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassBar(
                    backdrop = readerBackdrop,
                    corner = 0.dp,
                    blurEnabled = glassBlurEnabled,
                    liquid = liquidBackdrop,
                )
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (features.footerChapter) currentChapter ?: name else "",
                fontSize = 12.sp,
                color = palette.sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = infoRight,
                fontSize = 12.sp,
                color = palette.sub,
            )
        }
    }

    val readerView = LocalView.current
    SideEffect {
        // 系统栏图标明暗跟阅读页底色走（选了浅色预设时主题是深色也要用深色图标）
        val window = (context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, readerView)
        controller.isAppearanceLightStatusBars = !palette.dark
        controller.isAppearanceLightNavigationBars = !palette.dark
    }

    // 真沉浸（fork.29）：根背景用阅读页底色全幅铺满——状态栏与导航条全透明、
    // 背景延伸到两条系统栏之下；系统栏保持显示，内容在内层按 insets 让位
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg),
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
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
                                lineHeight = lineHeight,
                                paraSpacing = paraSpacing,
                                marginDp = marginDp,
                                palette = palette,
                                fontSpec = fontSpec,
                                indentChars = features.indentChars,
                                justify = features.justify,
                                pageAnim = features.pageAnim,
                                anchorPara = anchorPara,
                                anchorChar = anchorChar,
                                jumpPara = jumpPara,
                                jumpSignal = jumpSignal,
                                onPosition = { p, c ->
                                    anchorPara = p
                                    anchorChar = c
                                    positionReady = true
                                },
                                onPageInfo = { info -> pageInfo = info },
                                onTapCenter = { panelVisible = !panelVisible },
                                onLongPressPara = { para -> selectionPara = para },
                                modifier = contentModifier,
                            )
                        } else {
                            TxtScrollContent(
                                book = currentBook,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                paraSpacing = paraSpacing,
                                marginDp = marginDp,
                                textColor = palette.text,
                                fontSpec = fontSpec,
                                indentChars = features.indentChars,
                                justify = features.justify,
                                initialPara = anchorPara,
                                initialPx = when {
                                    anchorChar > 0 -> 0
                                    positionReady -> scrollPx
                                    else -> savedProgress?.offsetPx ?: 0
                                },
                                jumpPara = jumpPara,
                                jumpSignal = jumpSignal,
                                tapToggleEnabled = true,
                                onToggleMenu = { panelVisible = !panelVisible },
                                onLongPressPara = { para -> selectionPara = para },
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
                Box(Modifier.fillMaxSize()) {
                    // 采样录制层：只有正文（与非全屏的顶栏）进层；
                    // 玻璃浮层（信息行 / 底栏 / 呼出顶栏）一律在层外取样，避免自采样
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(readerBackdrop)
                            .kyantLayerBackdrop(readerLiquidBackdrop),
                    ) {
                        if (fullscreen) {
                            // 底部给浮着的玻璃信息行让位，正文不钻到它下面
                            content(
                                Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 40.dp),
                            )
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                topBar()
                                content(Modifier.weight(1f))
                                // 底部给浮着的玻璃底栏让位
                                Spacer(Modifier.height(48.dp))
                            }
                        }
                    }
                    if (fullscreen) {
                        Box(Modifier.align(Alignment.BottomCenter)) { infoRow() }
                        if (panelVisible) {
                            Column(Modifier.fillMaxSize()) {
                                slimTopBar()
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        Box(Modifier.align(Alignment.BottomCenter)) { bottomBar() }
                    }
                }
            }
        }

        // 控制面板（Legado 式，fork.28）：亮度一行、上一章/章节进度/下一章一行、
        // 目录/全文搜索/设置三入口一行；点按正文呼出，与快速设置面板分层
        if (panelVisible && currentBook != null) {
            TxtControlPanel(
                glassBackdrop = readerBackdrop,
                glassLiquid = liquidBackdrop,
                glassBlurEnabled = glassBlurEnabled,
                brightness = brightness,
                systemBrightness = systemBrightness,
                onBrightness = { value ->
                    brightness = value
                    TxtReaderStore.setBrightness(appContext, value)
                },
                canPrevChapter = chaptersNow.isNotEmpty() &&
                    (chapterIndex > 0 || (chapterIndex == 0 && anchorPara > chapterStartPara)),
                canNextChapter = chaptersNow.isNotEmpty() &&
                    chapterIndex < chaptersNow.size - 1,
                chapterProgress = if (chapterIndex >= 0 && chapterEndPara > chapterStartPara) {
                    (anchorPara - chapterStartPara).toFloat() /
                        (chapterEndPara - chapterStartPara)
                } else {
                    0f
                },
                onPrevChapter = {
                    val target = when {
                        chapterIndex > 0 && anchorPara <= chapterStartPara ->
                            chaptersNow[chapterIndex - 1].paragraphIndex
                        chapterIndex >= 0 -> chapterStartPara
                        else -> -1
                    }
                    if (target >= 0) {
                        jumpPara = target
                        jumpSignal++
                    }
                },
                onNextChapter = {
                    val next = chaptersNow.getOrNull(chapterIndex + 1)
                    if (next != null) {
                        jumpPara = next.paragraphIndex
                        jumpSignal++
                    }
                },
                onChapterSeek = { frac ->
                    if (chapterIndex >= 0) {
                        jumpPara = (chapterStartPara + frac * (chapterEndPara - chapterStartPara))
                            .toInt()
                            .coerceIn(
                                chapterStartPara,
                                (chapterEndPara - 1).coerceAtLeast(chapterStartPara),
                            )
                        jumpSignal++
                    }
                },
                onOpenToc = {
                    panelVisible = false
                    showToc = true
                },
                onOpenSearch = {
                    panelVisible = false
                    searchInitial = ""
                    showSearch = true
                },
                onOpenSettings = {
                    panelVisible = false
                    settingsNav.section = null
                    showSettings = true
                },
                onDismiss = { panelVisible = false },
            )
        }

        // 阅读设置（fork.29）：底部弹层承载统一的 TxtReaderSettingsContent，
        // 与书架「阅读设置」页同一组件、同一数据源；fork.28 的大而全覆盖层已删除，
        // 本书目录规则 / 编码 / 目录规则管理都收进组件的二级页，不再单开覆盖层
        if (showSettings && currentBook != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { showSettings = false },
            ) {
                TxtSheetCard(
                    glassBackdrop = readerBackdrop,
                    glassLiquid = liquidBackdrop,
                    glassBlurEnabled = glassBlurEnabled,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clickable(enabled = false) {},
                ) {
                    TxtReaderSettingsContent(
                        fontSize = fontSize,
                        onFontSizeChange = { fontSize = it },
                        onFontSizeCommit = { TxtReaderStore.setFontSize(appContext, fontSize) },
                        brightness = brightness,
                        systemBrightness = systemBrightness,
                        onBrightnessChange = { brightness = it },
                        onBrightnessCommit = {
                            TxtReaderStore.setBrightness(appContext, brightness)
                        },
                        readMode = readMode,
                        onReadModeChange = { value ->
                            if (readMode != value) {
                                readMode = value
                                if (value == TxtReaderStore.MODE_SCROLL) pageInfo = null
                                TxtReaderStore.setReadMode(appContext, value)
                            }
                        },
                        fullscreen = fullscreen,
                        onFullscreenChange = { value ->
                            fullscreen = value
                            TxtReaderStore.setFullscreen(appContext, value)
                        },
                        lineHeight = lineHeight,
                        onLineHeightChange = { lineHeight = it },
                        paraSpacing = paraSpacing,
                        onParaSpacingChange = { paraSpacing = it },
                        marginDp = marginDp,
                        onMarginChange = { marginDp = it },
                        onTypesetCommit = {
                            TxtReaderStore.setLineHeight(appContext, lineHeight)
                            TxtReaderStore.setParaSpacing(appContext, paraSpacing)
                            TxtReaderStore.setMargin(appContext, marginDp)
                        },
                        readerBg = readerBg,
                        onReaderBgChange = { value ->
                            readerBg = value
                            TxtReaderStore.setBgPreset(appContext, value)
                        },
                        tocRulesCount = tocRules.size,
                        tocRulesEnabled = tocRules.count { it.enabled },
                        onOpenTocRulesPage = null,
                        glassBackdrop = readerBackdrop,
                        glassLiquid = liquidBackdrop,
                        glassBlurEnabled = glassBlurEnabled,
                        book = TxtSettingsBookContext(
                            book = currentBook,
                            bookName = name,
                            encodingLabel = currentBook.encodingLabel,
                            encodingChoice = encodingChoice,
                            onEncodingChange = { encodingChoice = it },
                            tocRules = tocRules,
                            tocRuleChoice = ruleChoice,
                            onTocRuleChoice = { id ->
                                if (id != ruleChoice) {
                                    ruleChoice = id
                                    fileKey?.let { key ->
                                        TxtReaderStore.saveTocRuleChoice(
                                            appContext, space, key, id,
                                        )
                                    }
                                }
                            },
                            onTocRulesChange = ::applyRules,
                        ),
                        features = features,
                        nav = settingsNav,
                        modifier = if (settingsNav.section == null) {
                            Modifier.heightIn(max = 620.dp)
                        } else {
                            Modifier.height(600.dp)
                        },
                    )
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
                if (currentBook.chapters.isNotEmpty()) {
                    TextField(
                        value = tocQuery,
                        onValueChange = { tocQuery = it },
                        label = "搜索章节",
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "共 ${currentBook.chapters.size} 章",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = if (features.tocReversed) "倒序 ✓" else "倒序",
                            onClick = { features.setTocReversed(!features.tocReversed) },
                        )
                    }
                }
                if (currentBook.chapters.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "没有识别到章节标题，可在「设置 → 目录规则」里调整规则",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    val shownChapters = currentBook.chapters
                        .filter {
                            tocQuery.isBlank() ||
                                it.title.contains(tocQuery.trim(), ignoreCase = true)
                        }
                        .let { if (features.tocReversed) it.reversed() else it }
                    if (shownChapters.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "没有匹配的章节",
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(shownChapters.size) { index ->
                            val chapter = shownChapters[index]
                            val isCurrent = chapter.paragraphIndex ==
                                chaptersNow.getOrNull(chapterIndex)?.paragraphIndex
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

        // 全文搜索（fork.28）
        if (showSearch) {
            val searchBook = currentBook
            if (searchBook != null) {
                TxtSearchOverlay(
                    book = searchBook,
                    bookName = name,
                    initialQuery = searchInitial,
                    onJump = { para ->
                        showSearch = false
                        jumpPara = para
                        jumpSignal++
                    },
                    onDismiss = { showSearch = false },
                )
            }
        }

        // 长按选词层（fork.30）：长按正文段落呼出，两种阅读模式共用
        selectionPara?.let { para ->
            val selText = currentBook?.paragraphs?.getOrNull(para)
            if (selText != null) {
                TxtSelectionOverlay(
                    text = selText,
                    glassBackdrop = readerBackdrop,
                    glassLiquid = liquidBackdrop,
                    glassBlurEnabled = glassBlurEnabled,
                    onSearchWord = { word ->
                        selectionPara = null
                        searchInitial = word
                        showSearch = true
                    },
                    onDismiss = { selectionPara = null },
                )
            }
        }
        }
    }
}

/**
 * 控制面板（fork.28，Legado 式布局）：底部弹出条——亮度滑杆一行
 * （窗口亮度、持久化阅读偏好；「跟随系统」恢复不覆盖）、上一章/下一章
 * 夹章节进度滑杆一行（拖动在本章内定位）、目录/全文搜索/设置三入口一行。
 * 点面板外空白收起。
 */
@Composable
private fun TxtControlPanel(
    glassBackdrop: LayerBackdrop?,
    glassLiquid: com.kyant.backdrop.Backdrop?,
    glassBlurEnabled: Boolean,
    brightness: Float,
    systemBrightness: Float,
    onBrightness: (Float) -> Unit,
    canPrevChapter: Boolean,
    canNextChapter: Boolean,
    chapterProgress: Float,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onChapterSeek: (Float) -> Unit,
    onOpenToc: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    var brightnessDrag by remember { mutableStateOf<Float?>(null) }
    var chapterDrag by remember { mutableStateOf<Float?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                .glassBar(
                    backdrop = glassBackdrop,
                    corner = 24.dp,
                    blurEnabled = glassBlurEnabled,
                    liquid = glassLiquid,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "亮度",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Slider(
                    value = brightnessDrag
                        ?: if (brightness >= 0f) brightness else systemBrightness,
                    onValueChange = { brightnessDrag = it },
                    onValueChangeFinished = {
                        brightnessDrag?.let(onBrightness)
                        brightnessDrag = null
                    },
                    valueRange = 0.05f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                )
                TextButton(
                    text = "跟随系统",
                    onClick = { onBrightness(-1f) },
                    enabled = brightness >= 0f,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    text = "上一章",
                    onClick = onPrevChapter,
                    enabled = canPrevChapter,
                )
                Slider(
                    value = chapterDrag ?: chapterProgress.coerceIn(0f, 1f),
                    onValueChange = { chapterDrag = it },
                    onValueChangeFinished = {
                        chapterDrag?.let(onChapterSeek)
                        chapterDrag = null
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                )
                TextButton(
                    text = "下一章",
                    onClick = onNextChapter,
                    enabled = canNextChapter,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row {
                TxtPanelEntry("目录", onOpenToc, Modifier.weight(1f))
                TxtPanelEntry("全文搜索", onOpenSearch, Modifier.weight(1f))
                TxtPanelEntry("设置", onOpenSettings, Modifier.weight(1f))
            }
        }
    }
}

/**
 * 阅读器弹层卡片（fork.29）：有玻璃采样层时用与浏览器同款的 glassBar 玻璃底
 * （材质跟随全局 LocalBlurMode），没有时（书架侧规则页）退回原来的 Miuix Card。
 */
@Composable
private fun TxtSheetCard(
    glassBackdrop: LayerBackdrop?,
    glassLiquid: com.kyant.backdrop.Backdrop?,
    glassBlurEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (glassBackdrop == null && glassLiquid == null) {
        Card(modifier = modifier, insideMargin = PaddingValues(16.dp)) {
            Column(content = content)
        }
    } else {
        Column(
            modifier = modifier
                .glassBar(
                    backdrop = glassBackdrop,
                    corner = 24.dp,
                    blurEnabled = glassBlurEnabled,
                    liquid = glassLiquid,
                )
                .padding(16.dp),
            content = content,
        )
    }
}

/**
 * 目录规则管理主体（fork.28 抽出共用）：规则列表 + 导入/导出 + 编辑/粘贴弹层。
 * 阅读器内覆盖层与「阅读设置 → 目录规则」独立页共用这同一份实现，
 * 数据都走 TxtTocRules 全局存储，两处改动互通、不分叉。
 */
@Composable
fun TxtTocRulesManager(
    rules: List<TxtTocRule>,
    onRulesChange: (List<TxtTocRule>) -> Unit,
    modifier: Modifier = Modifier,
    glassBackdrop: LayerBackdrop? = null,
    glassLiquid: com.kyant.backdrop.Backdrop? = null,
    glassBlurEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var ruleEditor by remember { mutableStateOf<RuleEditRequest?>(null) }
    var showPaste by remember { mutableStateOf(false) }
    var importReport by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var menuRule by remember { mutableStateOf<TxtTocRule?>(null) }
    var showImportExport by remember { mutableStateOf(false) }

    fun importFromText(text: String) {
        val result = TxtTocRules.parseImport(text, System.currentTimeMillis())
        if (result.rules.isNotEmpty()) onRulesChange(rules + result.rules)
        importReport = "导入完成：成功 ${result.rules.size} 条，失败 ${result.failed} 条（已追加到列表末尾）"
        Diag.log("TxtReader rules import: ok=${result.rules.size} failed=${result.failed}")
    }

    val ruleImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?.let { String(it, Charsets.UTF_8) }
                    }.getOrNull()
                }
                if (text != null) importFromText(text)
                else importReport = "读取文件失败"
            }
        }
    }
    val ruleExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = pendingExport
        pendingExport = null
        if (uri != null && content != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")?.use {
                            it.write(content.toByteArray(Charsets.UTF_8))
                        }
                        true
                    }.getOrDefault(false)
                }
                Toast.makeText(context, if (ok) "规则已导出" else "导出失败", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    Box(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            if (importReport != null) {
                Text(
                    text = importReport!!,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(rules.size) { index ->
                    val rule = rules[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = rule.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = rule.pattern,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            AppSwitch(
                                checked = rule.enabled,
                                onCheckedChange = { enabled ->
                                    onRulesChange(
                                        rules.map {
                                            if (it.id == rule.id) {
                                                it.copy(enabled = enabled)
                                            } else {
                                                it
                                            }
                                        },
                                    )
                                },
                            )
                            TextButton(
                                text = "⋮",
                                onClick = { menuRule = rule },
                            )
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row {
                    TextButton(
                        text = "新增规则",
                        onClick = { ruleEditor = RuleEditRequest(null, "", "") },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "导入 / 导出",
                        onClick = { showImportExport = true },
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

        // 规则「更多」菜单（fork.29 收纳）：编辑 / 上移 / 下移 / 删除从卡面移入此处
        menuRule?.let { target ->
            val index = rules.indexOfFirst { it.id == target.id }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { menuRule = null },
            ) {
                TxtSheetCard(
                    glassBackdrop = glassBackdrop,
                    glassLiquid = glassLiquid,
                    glassBlurEnabled = glassBlurEnabled,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clickable(enabled = false) {},
                ) {
                    Column {
                        Text(
                            text = target.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            text = "编辑",
                            onClick = {
                                menuRule = null
                                ruleEditor = RuleEditRequest(target.id, target.name, target.pattern)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            text = "上移",
                            enabled = index > 0,
                            onClick = {
                                if (index > 0) {
                                    val next = rules.toMutableList()
                                    val tmp = next[index - 1]
                                    next[index - 1] = next[index]
                                    next[index] = tmp
                                    onRulesChange(next)
                                }
                                menuRule = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            text = "下移",
                            enabled = index >= 0 && index < rules.size - 1,
                            onClick = {
                                if (index >= 0 && index < rules.size - 1) {
                                    val next = rules.toMutableList()
                                    val tmp = next[index + 1]
                                    next[index + 1] = next[index]
                                    next[index] = tmp
                                    onRulesChange(next)
                                }
                                menuRule = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            text = "删除",
                            onClick = {
                                onRulesChange(rules.filter { it.id != target.id })
                                menuRule = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // 导入 / 导出二级弹层（fork.29 收纳）：四项动作从底部大按钮移入此处
        if (showImportExport) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { showImportExport = false },
            ) {
                TxtSheetCard(
                    glassBackdrop = glassBackdrop,
                    glassLiquid = glassLiquid,
                    glassBlurEnabled = glassBlurEnabled,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clickable(enabled = false) {},
                ) {
                    Column {
                        Text(
                            text = "导入 / 导出",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            text = "从文件导入",
                            onClick = {
                                showImportExport = false
                                ruleImportLauncher.launch(
                                    arrayOf("application/json", "text/plain", "*/*"),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            text = "粘贴导入",
                            onClick = {
                                showImportExport = false
                                showPaste = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            text = "复制导出",
                            onClick = {
                                showImportExport = false
                                clipboard.setText(AnnotatedString(TxtTocRules.exportJson(rules)))
                                Toast.makeText(context, "规则 JSON 已复制", Toast.LENGTH_SHORT)
                                    .show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            text = "导出到文件",
                            onClick = {
                                showImportExport = false
                                pendingExport = TxtTocRules.exportJson(rules)
                                ruleExportLauncher.launch("marea-toc-rules.json")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // 规则编辑（新增/编辑共用）
        ruleEditor?.let { request ->
            TxtRuleEditorOverlay(
                glassBackdrop = glassBackdrop,
                glassLiquid = glassLiquid,
                glassBlurEnabled = glassBlurEnabled,
                request = request,
                onDismiss = { ruleEditor = null },
                onDelete = if (request.id != null) {
                    {
                        onRulesChange(rules.filter { it.id != request.id })
                        ruleEditor = null
                    }
                } else {
                    null
                },
                onSave = { editedName, editedPattern ->
                    val updated = if (request.id == null) {
                        rules + TxtTocRule(
                            System.currentTimeMillis(), editedName, editedPattern, true,
                        )
                    } else {
                        rules.map {
                            if (it.id == request.id) {
                                it.copy(name = editedName, pattern = editedPattern)
                            } else {
                                it
                            }
                        }
                    }
                    onRulesChange(updated)
                    ruleEditor = null
                },
            )
        }

        // 粘贴导入
        if (showPaste) {
            TxtPasteImportOverlay(
                glassBackdrop = glassBackdrop,
                glassLiquid = glassLiquid,
                glassBlurEnabled = glassBlurEnabled,
                onDismiss = { showPaste = false },
                onImport = { text ->
                    showPaste = false
                    importFromText(text)
                },
            )
        }
    }
}

/** 控制面板的轻量文字入口（fork.29 瘦身）：比 TextButton 薄一截 */
@Composable
private fun TxtPanelEntry(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

private data class TxtRulePreview(val count: Int, val samples: List<String>)

/**
 * 本书选目录规则（fork.28，fork.29 改为可嵌入内容组件）：每条规则都在当前书的段落上实时试切，
 * 显示命中章节数与前 3 条章节标题供对比挑选；「自动」= 沿用全局
 * 规则列表顺序（卡面预览用父层已按全局口径切好的目录）。指定单条
 * 规则时按本书专用处理——忽略该规则在全局列表里的启停状态。
 */
@Composable
internal fun TxtBookRulePickerContent(
    book: TxtBook,
    rules: List<TxtTocRule>,
    choice: Long?,
    onPick: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var previews by remember { mutableStateOf<Map<Long, TxtRulePreview>>(emptyMap()) }
    LaunchedEffect(book, rules) {
        previews = withContext(Dispatchers.Default) {
            rules.associate { rule ->
                val chapters = TxtChapters.split(
                    book.paragraphs,
                    listOf(rule.copy(enabled = true)),
                )
                rule.id to TxtRulePreview(
                    chapters.size,
                    chapters.take(3).map { it.title },
                )
            }
        }
    }

    fun previewText(count: Int, samples: List<String>): String = when {
        count <= 0 -> "未切出章节"
        samples.isEmpty() -> "切出 $count 章"
        else -> "切出 $count 章：" + samples.joinToString(" · ")
    }

    Column(modifier = modifier) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { onPick(null) },
                    insideMargin = PaddingValues(14.dp),
                ) {
                    Column {
                        Text(
                            text = if (choice == null) "✓ 自动（全局规则顺序）" else "自动（全局规则顺序）",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = previewText(
                                book.chapters.size,
                                book.chapters.take(3).map { it.title },
                            ),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            items(rules.size) { index ->
                val rule = rules[index]
                val preview = previews[rule.id]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { onPick(rule.id) },
                    insideMargin = PaddingValues(14.dp),
                ) {
                    Column {
                        Text(
                            text = buildString {
                                if (choice == rule.id) append("✓ ")
                                append(rule.name)
                                if (!rule.enabled) append("（全局停用，本书仍可用）")
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (preview == null) {
                                "试切中…"
                            } else {
                                previewText(preview.count, preview.samples)
                            },
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Text(
            text = "预览为每条规则单独在本书上试切的结果；选定后目录立即按新规则重切，阅读位置保持不变。",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}


/**
 * 长按选词层（fork.30）：长按正文段落呼出，该段以只读文本框呈现——
 * 系统选词手柄可拖选，选区从 TextFieldValue 读出。动作三项：复制
 * （未选中时作用于整段）、按选中词跳全文搜索、分享选中文本。
 * 玻璃走阅读器既有 TxtSheetCard + glassBar 采样，与设置弹层同款。
 */
@Composable
private fun TxtSelectionOverlay(
    text: String,
    glassBackdrop: LayerBackdrop?,
    glassLiquid: com.kyant.backdrop.Backdrop?,
    glassBlurEnabled: Boolean,
    onSearchWord: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var fieldValue by remember(text) {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(text))
    }
    val selection = fieldValue.selection
    val selected = if (selection.collapsed) {
        ""
    } else {
        text.substring(
            selection.start.coerceIn(0, text.length),
            selection.end.coerceIn(0, text.length),
        )
    }
    val actionText = selected.ifEmpty { text }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable { onDismiss() },
    ) {
        TxtSheetCard(
            glassBackdrop = glassBackdrop,
            glassLiquid = glassLiquid,
            glassBlurEnabled = glassBlurEnabled,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .clickable(enabled = false) {},
        ) {
            Column {
                Text(
                    text = "选择文字",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = fieldValue,
                    onValueChange = { fieldValue = it },
                    readOnly = true,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (selected.isEmpty()) {
                        "拖动选词手柄选中文字；未选中时「复制 / 分享」作用于整段"
                    } else {
                        "已选中 ${selected.length} 字"
                    },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    TextButton(
                        text = "复制",
                        onClick = {
                            clipboard.setText(AnnotatedString(actionText))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "搜全书",
                        enabled = selected.isNotBlank(),
                        onClick = { onSearchWord(selected.trim()) },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "分享",
                        onClick = {
                            runCatching {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, actionText)
                                }
                                context.startActivity(Intent.createChooser(send, null))
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private data class TxtSearchHit(val paragraph: Int, val chapter: String?, val snippet: String)

/**
 * 全文搜索（fork.28）：在当前书全段落里做不区分大小写的包含匹配，
 * 结果带命中点前后文片段与所属章节；点结果按段落锚点跳转
 * （滚动模式跳段、翻页模式经锚点换算到对应页）。最多保留 300 条。
 */
@Composable
private fun TxtSearchOverlay(
    book: TxtBook,
    bookName: String,
    initialQuery: String = "",
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery) }
    var hits by remember { mutableStateOf<List<TxtSearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query, book) {
        val q = query.trim()
        if (q.isEmpty()) {
            hits = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(250)
        hits = withContext(Dispatchers.Default) {
            val out = ArrayList<TxtSearchHit>()
            for ((index, para) in book.paragraphs.withIndex()) {
                val at = para.indexOf(q, ignoreCase = true)
                if (at < 0) continue
                val from = (at - 18).coerceAtLeast(0)
                val to = (at + q.length + 22).coerceAtMost(para.length)
                val snippet = buildString {
                    if (from > 0) append("…")
                    append(para.substring(from, to))
                    if (to < para.length) append("…")
                }
                val chapter = book.chapters
                    .lastOrNull { it.paragraphIndex <= index }?.title
                out.add(TxtSearchHit(index, chapter, snippet))
                if (out.size >= 300) break
            }
            out
        }
        searching = false
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
    ) {
        SmallTopAppBar(
            title = "全文搜索",
            subtitle = bookName,
            navigationIcon = {
                LiquidGlassBackButton(backdrop = null, surfaceVisible = true, onClick = onDismiss)
            },
        )
        TextField(
            value = query,
            onValueChange = { query = it },
            label = "搜索关键词",
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = when {
                query.isBlank() -> "在全书段落中搜索，结果点按跳转"
                searching -> "搜索中…"
                hits.size >= 300 -> "命中 300+ 处（仅显示前 300 条）"
                else -> "命中 ${hits.size} 处"
            },
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(hits.size) { index ->
                val hit = hits[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { onJump(hit.paragraph) },
                    insideMargin = PaddingValues(14.dp),
                ) {
                    Column {
                        if (hit.chapter != null) {
                            Text(
                                text = hit.chapter,
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(
                            text = hit.snippet,
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TxtRuleEditorOverlay(
    glassBackdrop: LayerBackdrop?,
    glassLiquid: com.kyant.backdrop.Backdrop?,
    glassBlurEnabled: Boolean,
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
        TxtSheetCard(
            glassBackdrop = glassBackdrop,
            glassLiquid = glassLiquid,
            glassBlurEnabled = glassBlurEnabled,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .clickable(enabled = false) {},
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
    glassBackdrop: LayerBackdrop?,
    glassLiquid: com.kyant.backdrop.Backdrop?,
    glassBlurEnabled: Boolean,
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
        TxtSheetCard(
            glassBackdrop = glassBackdrop,
            glassLiquid = glassLiquid,
            glassBlurEnabled = glassBlurEnabled,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .clickable(enabled = false) {},
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
    lineHeight: Float,
    paraSpacing: Float,
    marginDp: Float,
    textColor: Color,
    fontSpec: TxtFontSpec,
    indentChars: Int,
    justify: Boolean,
    initialPara: Int,
    initialPx: Int,
    jumpPara: Int,
    jumpSignal: Int,
    tapToggleEnabled: Boolean,
    onToggleMenu: () -> Unit,
    onLongPressPara: (Int) -> Unit,
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
    val align = if (justify) {
        androidx.compose.ui.text.style.TextAlign.Justify
    } else {
        androidx.compose.ui.text.style.TextAlign.Left
    }
    val indentPrefix = if (indentChars > 0) "　".repeat(indentChars) else ""
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = marginDp.dp, vertical = 12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(book.paragraphs.size) { index ->
            val isChapter = index in book.chapterStarts
            Text(
                // 首行缩进（fork.30）：只在渲染文本前补全角空格，段落原文不动
                text = if (!isChapter && indentPrefix.isNotEmpty()) {
                    indentPrefix + book.paragraphs[index]
                } else {
                    book.paragraphs[index]
                },
                style = TextStyle(
                    fontSize = if (isChapter) (fontSize + 2).sp else fontSize.sp,
                    lineHeight = ((if (isChapter) fontSize + 2 else fontSize) * lineHeight).sp,
                    fontWeight = if (isChapter) FontWeight.SemiBold else fontSpec.weight,
                    fontFamily = fontSpec.family,
                    textAlign = align,
                ),
                color = textColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (tapToggleEnabled) {
                            Modifier.combinedClickable(
                                onClick = onToggleMenu,
                                onLongClick = { onLongPressPara(index) },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(
                        top = if (isChapter && index > 0) (paraSpacing * 2 + 4).dp else 0.dp,
                        bottom = paraSpacing.dp,
                    ),
            )
        }
    }
}

/* ---------------- 翻页正文（fork.27 平移，fork.30 扩五种动画 + 长按选词） ---------------- */

@Composable
private fun TxtPageContent(
    book: TxtBook,
    fontSize: Float,
    lineHeight: Float,
    paraSpacing: Float,
    marginDp: Float,
    palette: TxtReaderPalette,
    fontSpec: TxtFontSpec,
    indentChars: Int,
    justify: Boolean,
    pageAnim: String,
    anchorPara: Int,
    anchorChar: Int,
    jumpPara: Int,
    jumpSignal: Int,
    onPosition: (Int, Int) -> Unit,
    onPageInfo: (TxtPageInfo) -> Unit,
    onTapCenter: () -> Unit,
    onLongPressPara: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val pages = remember(
            book, fontSize, lineHeight, paraSpacing, marginDp,
            fontSpec, indentChars, justify, widthPx, heightPx,
        ) {
            paginateTxt(
                measurer = measurer,
                paragraphs = book.paragraphs,
                chapterStarts = book.chapterStarts,
                fontSizeSp = fontSize,
                contentWidthPx = (widthPx - with(density) { (marginDp * 2).dp.toPx() }).toInt(),
                contentHeightPx = (heightPx - with(density) { 32.dp.toPx() }).toInt(),
                paragraphBottomPx = with(density) { paraSpacing.dp.toPx() },
                chapterTopPx = with(density) { (paraSpacing * 2 + 4).dp.toPx() },
                lineHeightFactor = lineHeight,
                fontFamily = fontSpec.family,
                fontWeight = fontSpec.weight,
                indentChars = indentChars,
                justify = justify,
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
            // 本章页码（Legado 底部信息行口径）：页按其起始段落归属章节统计
            val chIdx = book.chapters.indexOfLast { it.paragraphIndex <= p.startParagraph }
            if (chIdx >= 0) {
                val start = book.chapters[chIdx].paragraphIndex
                val end = book.chapters.getOrNull(chIdx + 1)?.paragraphIndex
                    ?: book.paragraphs.size
                var count = 0
                var mine = 0
                pages.forEachIndexed { i, pg ->
                    if (pg.startParagraph >= start && pg.startParagraph < end) {
                        count++
                        if (i == pageIndex) mine = count
                    }
                }
                onPageInfo(TxtPageInfo(pageIndex + 1, pages.size, mine, count))
            } else {
                onPageInfo(TxtPageInfo(pageIndex + 1, pages.size, 0, 0))
            }
        }
        val vertical = pageAnim == TxtReaderStore.ANIM_VERTICAL
        val axisSize = if (vertical) heightPx else widthPx
        val anim = remember { Animatable(0f) }
        fun flip(delta: Int) {
            val target = pageIndex + delta
            if (target !in pages.indices) return
            if (pageAnim == TxtReaderStore.ANIM_NONE) {
                pageIndex = target
                return
            }
            if (anim.isRunning) return
            scope.launch {
                anim.animateTo(if (delta > 0) -axisSize else axisSize, tween(230))
                pageIndex = target
                anim.snapTo(0f)
            }
        }
        // 长按选词命中表：当前页各段落在页内的纵向范围，静止时由当前页列回报
        val sliceBounds = remember { mutableStateMapOf<Int, IntRange>() }
        LaunchedEffect(pageIndex) { sliceBounds.clear() }
        val onSliceBounds: (Int, Int, Int) -> Unit = { para, top, bottom ->
            val old = sliceBounds[para]
            sliceBounds[para] = if (old == null) {
                top..bottom
            } else {
                minOf(old.first, top)..maxOf(old.last, bottom)
            }
        }

        var gestures: Modifier = Modifier
        if (pageAnim != TxtReaderStore.ANIM_NONE) {
            gestures = if (vertical) {
                gestures.pointerInput(pageIndex, pages.size, pageAnim) {
                    detectVerticalDragGestures(
                        onDragStart = { scope.launch { anim.stop() } },
                        onDragEnd = {
                            val v = anim.value
                            when {
                                v < -axisSize * 0.22f && pageIndex < pages.size - 1 -> flip(1)
                                v > axisSize * 0.22f && pageIndex > 0 -> flip(-1)
                                else -> scope.launch { anim.animateTo(0f, tween(200)) }
                            }
                        },
                        onDragCancel = { scope.launch { anim.animateTo(0f, tween(200)) } },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            scope.launch {
                                anim.snapTo(
                                    (anim.value + amount).coerceIn(-axisSize, axisSize),
                                )
                            }
                        },
                    )
                }
            } else {
                gestures.pointerInput(pageIndex, pages.size, pageAnim) {
                    detectHorizontalDragGestures(
                        onDragStart = { scope.launch { anim.stop() } },
                        onDragEnd = {
                            val v = anim.value
                            when {
                                v < -axisSize * 0.22f && pageIndex < pages.size - 1 -> flip(1)
                                v > axisSize * 0.22f && pageIndex > 0 -> flip(-1)
                                else -> scope.launch { anim.animateTo(0f, tween(200)) }
                            }
                        },
                        onDragCancel = { scope.launch { anim.animateTo(0f, tween(200)) } },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            scope.launch {
                                anim.snapTo(
                                    (anim.value + amount).coerceIn(-axisSize, axisSize),
                                )
                            }
                        },
                    )
                }
            }
        }
        gestures = gestures.pointerInput(pageIndex, pages.size, pageAnim) {
            detectTapGestures(
                onTap = { offset ->
                    if (anim.value != 0f) return@detectTapGestures
                    when {
                        vertical && offset.y < heightPx / 3f -> flip(-1)
                        vertical && offset.y > heightPx * 2f / 3f -> flip(1)
                        !vertical && offset.x < widthPx / 3f -> flip(-1)
                        !vertical && offset.x > widthPx * 2f / 3f -> flip(1)
                        else -> onTapCenter()
                    }
                },
                onLongPress = { offset ->
                    if (anim.value != 0f) return@detectTapGestures
                    val para = sliceBounds.entries
                        .firstOrNull { offset.y.toInt() in it.value }?.key
                    if (para != null) onLongPressPara(para)
                },
            )
        }

        val current = pages.getOrNull(pageIndex)
        val nextPage = pages.getOrNull(pageIndex + 1)
        val prevPage = pages.getOrNull(pageIndex - 1)
        val off = anim.value

        @Composable
        fun pageColumn(page: TxtPage, report: Boolean, mod: Modifier) {
            TxtPageColumn(
                page = page,
                fontSize = fontSize,
                lineHeight = lineHeight,
                paraSpacing = paraSpacing,
                marginDp = marginDp,
                textColor = palette.text,
                fontSpec = fontSpec,
                indentChars = indentChars,
                justify = justify,
                reportBounds = report,
                onSliceBounds = onSliceBounds,
                modifier = mod,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .then(gestures),
        ) {
            when {
                current == null -> Unit
                pageAnim == TxtReaderStore.ANIM_NONE || off == 0f -> {
                    pageColumn(current, true, Modifier.fillMaxSize())
                }
                pageAnim == TxtReaderStore.ANIM_COVER -> {
                    // 覆盖：下一页自右盖入；上一页靠当前页向右滑开露出
                    if (off < 0f) {
                        pageColumn(current, false, Modifier.fillMaxSize())
                        if (nextPage != null) {
                            pageColumn(
                                nextPage, false,
                                Modifier.fillMaxSize().offset {
                                    IntOffset((widthPx + off).roundToInt(), 0)
                                },
                            )
                        }
                    } else {
                        if (prevPage != null) pageColumn(prevPage, false, Modifier.fillMaxSize())
                        pageColumn(
                            current, false,
                            Modifier.fillMaxSize().offset { IntOffset(off.roundToInt(), 0) },
                        )
                    }
                }
                pageAnim == TxtReaderStore.ANIM_CURL -> {
                    // 仿真（简化卷页近似）：下一页在下层，当前页从右向左被卷走——
                    // 只绘制未卷部分并在卷边叠压暗/亮边/投影渐变，不做物理引擎
                    val fullWidthDp = with(density) { widthPx.toDp() }
                    if (off < 0f) {
                        if (nextPage != null) pageColumn(nextPage, false, Modifier.fillMaxSize())
                        val visiblePx = (widthPx + off).coerceAtLeast(0f)
                        val visibleDp = with(density) { visiblePx.toDp() }
                        Box(
                            modifier = Modifier
                                .width(visibleDp)
                                .fillMaxHeight()
                                .clipToBounds(),
                        ) {
                            pageColumn(
                                current, false,
                                Modifier.requiredWidth(fullWidthDp).fillMaxHeight(),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(30.dp)
                                .offset(x = visibleDp - 30.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.25f),
                                        ),
                                    ),
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(24.dp)
                                .offset(x = visibleDp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.20f),
                                            Color.Black.copy(alpha = 0.18f),
                                            Color.Transparent,
                                        ),
                                    ),
                                ),
                        )
                    } else {
                        if (prevPage != null) pageColumn(prevPage, false, Modifier.fillMaxSize())
                        pageColumn(
                            current, false,
                            Modifier.fillMaxSize().offset { IntOffset(off.roundToInt(), 0) },
                        )
                        val edgeDp = with(density) { off.toDp() }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(24.dp)
                                .offset(x = edgeDp - 24.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.20f),
                                        ),
                                    ),
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(30.dp)
                                .offset(x = edgeDp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.22f),
                                            Color.White.copy(alpha = 0.10f),
                                            Color.Transparent,
                                        ),
                                    ),
                                ),
                        )
                    }
                }
                vertical -> {
                    pageColumn(
                        current, false,
                        Modifier.fillMaxSize().offset { IntOffset(0, off.roundToInt()) },
                    )
                    if (off < 0f && nextPage != null) {
                        pageColumn(
                            nextPage, false,
                            Modifier.fillMaxSize().offset {
                                IntOffset(0, (off + heightPx).roundToInt())
                            },
                        )
                    }
                    if (off > 0f && prevPage != null) {
                        pageColumn(
                            prevPage, false,
                            Modifier.fillMaxSize().offset {
                                IntOffset(0, (off - heightPx).roundToInt())
                            },
                        )
                    }
                }
                else -> {
                    // 左右平移（fork.27 原样）
                    pageColumn(
                        current, false,
                        Modifier.fillMaxSize().offset { IntOffset(off.roundToInt(), 0) },
                    )
                    if (off < 0f && nextPage != null) {
                        pageColumn(
                            nextPage, false,
                            Modifier.fillMaxSize().offset {
                                IntOffset((off + widthPx).roundToInt(), 0)
                            },
                        )
                    }
                    if (off > 0f && prevPage != null) {
                        pageColumn(
                            prevPage, false,
                            Modifier.fillMaxSize().offset {
                                IntOffset((off - widthPx).roundToInt(), 0)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TxtPageColumn(
    page: TxtPage,
    fontSize: Float,
    lineHeight: Float,
    paraSpacing: Float,
    marginDp: Float,
    textColor: Color,
    fontSpec: TxtFontSpec,
    indentChars: Int,
    justify: Boolean,
    reportBounds: Boolean,
    onSliceBounds: (Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val align = if (justify) {
        androidx.compose.ui.text.style.TextAlign.Justify
    } else {
        androidx.compose.ui.text.style.TextAlign.Left
    }
    Column(modifier.padding(horizontal = marginDp.dp, vertical = 16.dp)) {
        page.slices.forEachIndexed { idx, slice ->
            Text(
                // 首行缩进（fork.30）：段首切片渲染时补全角空格；切片文本与
                // 字符偏移仍是原段坐标，缩进不进存储、不污染锚点
                text = if (!slice.chapter && slice.startChar == 0 && indentChars > 0) {
                    "　".repeat(indentChars) + slice.text
                } else {
                    slice.text
                },
                style = TextStyle(
                    fontSize = if (slice.chapter) (fontSize + 2).sp else fontSize.sp,
                    lineHeight = ((if (slice.chapter) fontSize + 2 else fontSize) * lineHeight).sp,
                    fontWeight = if (slice.chapter) FontWeight.SemiBold else fontSpec.weight,
                    fontFamily = fontSpec.family,
                    textAlign = align,
                ),
                color = textColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (reportBounds) {
                            Modifier.onGloballyPositioned { coords ->
                                val pos = coords.positionInParent()
                                onSliceBounds(
                                    slice.paragraph,
                                    pos.y.toInt(),
                                    (pos.y + coords.size.height).toInt(),
                                )
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(
                        top = if (slice.chapter && idx > 0) (paraSpacing * 2 + 4).dp else 0.dp,
                        bottom = paraSpacing.dp,
                    ),
            )
        }
    }
}
