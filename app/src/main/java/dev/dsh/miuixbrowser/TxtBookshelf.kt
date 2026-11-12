package dev.dsh.miuixbrowser

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 书架上的一本书（fork.27，fork.30 扩字段）：只存定位与展示信息，不存正文。
 * fork.30 起加 addedAt（入架时间，排序用）、pinned（置顶）、updatedAt
 * （记录变更时间，WebDAV 合并的时间基准）；旧数据缺这些字段时读入给
 * 默认值（addedAt/updatedAt 回落 lastRead、pinned=false），书目不丢。 */
data class TxtShelfEntry(
    val key: String,
    val uri: String,
    val name: String,
    val bytes: Int,
    val lastRead: Long,
    val addedAt: Long = 0L,
    val pinned: Boolean = false,
    val updatedAt: Long = 0L,
)

/**
 * 书架存储（fork.27）。与阅读进度同口径分空间：隐私空间打开过的书
 * 只进隐私书架（独立 prefs 文件），普通空间不可见。阅读器每次打开
 * 文件都会 upsert 一条并刷新 lastRead；「从书架移除」只删这条记录，
 * 不动源文件与阅读进度。
 */
object TxtBookshelfStore {

    private fun prefs(context: Context, space: Int) =
        context.getSharedPreferences(
            if (space == 1) "marea_txt_bookshelf_private" else "marea_txt_bookshelf",
            Context.MODE_PRIVATE,
        )

    fun load(context: Context, space: Int): List<TxtShelfEntry> {
        val raw = runCatching { prefs(context, space).getString("books", null) }.getOrNull()
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val out = ArrayList<TxtShelfEntry>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val key = o.optString("key")
                val uri = o.optString("uri")
                if (key.isBlank() || uri.isBlank()) continue
                val lastRead = o.optLong("lastRead", 0L)
                out += TxtShelfEntry(
                    key = key,
                    uri = uri,
                    name = o.optString("name").ifBlank { "TXT 文档" },
                    bytes = o.optInt("bytes", 0),
                    lastRead = lastRead,
                    addedAt = o.optLong("addedAt", lastRead),
                    pinned = o.optBoolean("pinned", false),
                    updatedAt = o.optLong("updatedAt", lastRead),
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    private fun saveAll(context: Context, space: Int, entries: List<TxtShelfEntry>) {
        runCatching {
            val array = JSONArray()
            entries.forEach { e ->
                array.put(
                    JSONObject()
                        .put("key", e.key)
                        .put("uri", e.uri)
                        .put("name", e.name)
                        .put("bytes", e.bytes)
                        .put("lastRead", e.lastRead)
                        .put("addedAt", e.addedAt)
                        .put("pinned", e.pinned)
                        .put("updatedAt", e.updatedAt),
                )
            }
            prefs(context, space).edit().putString("books", array.toString()).apply()
        }
    }

    fun upsert(context: Context, space: Int, key: String, uri: String, name: String, bytes: Int) {
        val current = load(context, space).toMutableList()
        // 刷新阅读时间时保留原入架时间与置顶标记（fork.30）
        val old = current.firstOrNull { it.key == key }
        current.removeAll { it.key == key }
        val now = System.currentTimeMillis()
        current.add(
            0,
            TxtShelfEntry(
                key = key,
                uri = uri,
                name = name,
                bytes = bytes,
                lastRead = now,
                addedAt = old?.addedAt?.takeIf { it > 0 } ?: now,
                pinned = old?.pinned ?: false,
                updatedAt = now,
            ),
        )
        saveAll(context, space, current)
    }

    fun remove(context: Context, space: Int, key: String) {
        saveAll(context, space, load(context, space).filter { it.key != key })
    }

    /** 批量移除（fork.30 多选）：只删书架记录，口径与单条移除一致 */
    fun removeKeys(context: Context, space: Int, keys: Set<String>) {
        saveAll(context, space, load(context, space).filter { it.key !in keys })
    }

    /** 置顶 / 取消置顶（fork.30）：置顶是记录标记，展示排序时恒排在前 */
    fun setPinned(context: Context, space: Int, keys: Set<String>, pinned: Boolean) {
        val now = System.currentTimeMillis()
        saveAll(
            context, space,
            load(context, space).map {
                if (it.key in keys) it.copy(pinned = pinned, updatedAt = now) else it
            },
        )
    }

    /** WebDAV 合入远端书目（fork.30）：整条原样落盘，时间戳以记录自带为准 */
    fun mergeEntry(context: Context, space: Int, entry: TxtShelfEntry) {
        val current = load(context, space).toMutableList()
        current.removeAll { it.key == entry.key }
        current.add(entry)
        saveAll(context, space, current)
    }
}

/** 书名展示：文件名去掉 .txt 扩展名 */
private fun shelfTitle(name: String): String =
    name.replace(Regex("(?i)\\.txt$"), "").ifBlank { name }

private fun readBytesForShelf(context: Context, uri: Uri): ByteArray {
    val stream = if (uri.scheme == ContentResolver.SCHEME_FILE) {
        File(uri.path ?: throw IllegalStateException("bad file uri")).inputStream()
    } else {
        context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("openInputStream returned null")
    }
    return stream.use { it.readBytes() }
}

private fun shelfDisplayName(context: Context, uri: Uri): String {
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
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

/** 累计阅读时长展示（fork.30）：0 不显示，不足 1 分钟显示「<1 分钟」 */
private fun formatReadDuration(totalSeconds: Long): String = when {
    totalSeconds <= 0 -> ""
    totalSeconds < 60 -> "读过 <1 分钟"
    totalSeconds < 3600 -> "读过 ${totalSeconds / 60} 分钟"
    else -> {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        if (m > 0) "读过 $h 小时 $m 分" else "读过 $h 小时"
    }
}

@Composable
private fun ShelfSortChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = if (selected) MiuixTheme.colorScheme.onPrimary
            else MiuixTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 书架页（fork.27，fork.30 增强）：当前空间的书目列表。点卡片继续读
 * （带进度），长按进入多选模式（批量移除 / 置顶，沿用长按入口，只删
 * 记录不删源文件）；顶部可搜书名、三档排序（最近阅读 / 书名 / 添加
 * 时间，置顶恒在前）；卡片显示进度、累计阅读时长与上次阅读时间；
 * 打开书架时静默 WebDAV 同步一次。
 */
@Composable
fun BookshelfScreen(space: Int, onBack: () -> Unit, onOpenReaderSettings: () -> Unit) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(TxtBookshelfStore.load(context, space)) }
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(TxtReaderStore.shelfSort(context)) }
    var selected by remember { mutableStateOf<Set<String>?>(null) }
    var confirmBatchRemove by remember { mutableStateOf(false) }

    // 打开书架时静默同步（fork.30）：配过 WebDAV 才真正跑，失败静默；
    // 同步可能合入远端书目，回来后重读一次本地书架
    LaunchedEffect(Unit) {
        TxtSync.syncIfConfigured(appContext)
        entries = TxtBookshelfStore.load(appContext, space)
    }

    val addLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                for (uri in uris) {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    runCatching {
                        val bytes = withContext(Dispatchers.IO) { readBytesForShelf(context, uri) }
                        val key = TxtReaderStore.fileKey(uri, bytes.size)
                        TxtBookshelfStore.upsert(
                            appContext, space, key, uri.toString(),
                            shelfDisplayName(context, uri), bytes.size,
                        )
                    }
                }
                entries = TxtBookshelfStore.load(appContext, space)
            }
        }
    }

    val comparator = when (sortMode) {
        "name" -> compareByDescending<TxtShelfEntry> { it.pinned }
            .thenBy { shelfTitle(it.name) }
        "added" -> compareByDescending<TxtShelfEntry> { it.pinned }
            .thenByDescending { it.addedAt }
        else -> compareByDescending<TxtShelfEntry> { it.pinned }
            .thenByDescending { it.lastRead }
    }
    val shown = entries
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .sortedWith(comparator)
    val inSelection = selected != null
    val selectedCount = selected?.size ?: 0

    fun applySort(mode: String) {
        sortMode = mode
        TxtReaderStore.setShelfSort(appContext, mode)
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = if (inSelection) "已选 $selectedCount 本" else "书架",
                subtitle = if (space == 1) "隐私空间" else "${entries.size} 本",
                navigationIcon = {
                    LiquidGlassBackButton(
                        backdrop = null,
                        surfaceVisible = true,
                        onClick = { if (inSelection) selected = null else onBack() },
                    )
                },
                actions = {
                    if (inSelection) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                text = "置顶",
                                enabled = selectedCount > 0,
                                onClick = {
                                    selected?.let {
                                        TxtBookshelfStore.setPinned(appContext, space, it, true)
                                    }
                                    entries = TxtBookshelfStore.load(appContext, space)
                                    selected = null
                                },
                            )
                            TextButton(
                                text = "取消置顶",
                                enabled = selectedCount > 0,
                                onClick = {
                                    selected?.let {
                                        TxtBookshelfStore.setPinned(appContext, space, it, false)
                                    }
                                    entries = TxtBookshelfStore.load(appContext, space)
                                    selected = null
                                },
                            )
                            TextButton(
                                text = "移除",
                                enabled = selectedCount > 0,
                                onClick = { confirmBatchRemove = true },
                            )
                            TextButton(
                                text = "完成",
                                onClick = { selected = null },
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onOpenReaderSettings) {
                                Icon(
                                    imageVector = MiuixIcons.Glass.Settings,
                                    contentDescription = "阅读设置",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                            TextButton(
                                text = "添加书籍",
                                onClick = { addLauncher.launch(arrayOf("text/plain")) },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MiuixTheme.colorScheme.surface),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (!inSelection && entries.isNotEmpty()) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        label = "搜索书名",
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShelfSortChip("最近阅读", sortMode == "recent") { applySort("recent") }
                        Spacer(Modifier.width(8.dp))
                        ShelfSortChip("书名", sortMode == "name") { applySort("name") }
                        Spacer(Modifier.width(8.dp))
                        ShelfSortChip("添加时间", sortMode == "added") { applySort("added") }
                    }
                }
                when {
                    entries.isEmpty() -> Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "书架还是空的",
                                fontSize = 15.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "打开过的 txt 会自动放进来，也可以点右上角「添加书籍」",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    shown.isEmpty() -> Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "没有匹配的书",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(shown.size) { index ->
                            val entry = shown[index]
                            val progress = remember(entry.key, entry.lastRead) {
                                TxtReaderStore.loadProgress(appContext, space, entry.key)
                            }
                            val readSecs = remember(entry.key, entry.lastRead) {
                                TxtReaderStore.readSeconds(appContext, space, entry.key)
                            }
                            val percent = when {
                                progress == null || progress.total <= 0 -> null
                                else -> (progress.paragraph * 100 / progress.total)
                                    .coerceIn(0, 100)
                            }
                            val isChecked = selected?.contains(entry.key) == true
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (inSelection) {
                                                val current = selected ?: emptySet()
                                                selected = if (entry.key in current) {
                                                    current - entry.key
                                                } else {
                                                    current + entry.key
                                                }
                                            } else {
                                                runCatching {
                                                    context.startActivity(
                                                        TxtReaderActivity.intent(
                                                            context,
                                                            Uri.parse(entry.uri),
                                                            entry.name,
                                                            space,
                                                        ),
                                                    )
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            if (!inSelection) selected = setOf(entry.key)
                                        },
                                    ),
                                insideMargin = PaddingValues(16.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (inSelection) {
                                        Text(
                                            text = if (isChecked) "●" else "○",
                                            fontSize = 15.sp,
                                            color = if (isChecked) {
                                                MiuixTheme.colorScheme.primary
                                            } else {
                                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                                            },
                                        )
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = shelfTitle(entry.name),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MiuixTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (entry.pinned) {
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = "置顶",
                                                    fontSize = 11.sp,
                                                    color = MiuixTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = listOf(
                                                    when {
                                                        percent == null -> "未开始"
                                                        percent >= 100 -> "已读完"
                                                        else -> "已读 $percent%"
                                                    },
                                                    formatReadDuration(readSecs),
                                                ).filter { it.isNotEmpty() }
                                                    .joinToString(" · "),
                                                fontSize = 13.sp,
                                                color = MiuixTheme.colorScheme.primary,
                                            )
                                            Spacer(Modifier.weight(1f))
                                            Text(
                                                text = if (entry.lastRead > 0) {
                                                    SimpleDateFormat(
                                                        "MM-dd HH:mm",
                                                        Locale.getDefault(),
                                                    ).format(Date(entry.lastRead))
                                                } else {
                                                    ""
                                                },
                                                fontSize = 12.sp,
                                                color = MiuixTheme.colorScheme
                                                    .onSurfaceVariantSummary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 批量移除确认（页内覆盖层）：只删书架记录，不删源文件与进度
            if (confirmBatchRemove) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .combinedClickable(onClick = { confirmBatchRemove = false }),
                ) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(12.dp),
                        insideMargin = PaddingValues(16.dp),
                    ) {
                        Column {
                            Text(
                                text = "从书架移除",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "将把选中的 $selectedCount 本书从书架列表里去掉，" +
                                    "源文件和阅读进度都不删。",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row {
                                Spacer(Modifier.weight(1f))
                                TextButton(
                                    text = "取消",
                                    onClick = { confirmBatchRemove = false },
                                )
                                Spacer(Modifier.padding(horizontal = 4.dp))
                                TextButton(
                                    text = "移除",
                                    onClick = {
                                        selected?.let {
                                            TxtBookshelfStore.removeKeys(
                                                appContext, space, it,
                                            )
                                        }
                                        entries = TxtBookshelfStore.load(appContext, space)
                                        selected = null
                                        confirmBatchRemove = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
