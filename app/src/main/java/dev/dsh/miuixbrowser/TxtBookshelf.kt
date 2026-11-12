package dev.dsh.miuixbrowser

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 书架上的一本书（fork.27）：只存定位与展示信息，不存正文 */
data class TxtShelfEntry(
    val key: String,
    val uri: String,
    val name: String,
    val bytes: Int,
    val lastRead: Long,
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
                out += TxtShelfEntry(
                    key = key,
                    uri = uri,
                    name = o.optString("name").ifBlank { "TXT 文档" },
                    bytes = o.optInt("bytes", 0),
                    lastRead = o.optLong("lastRead", 0L),
                )
            }
            out.sortedByDescending { it.lastRead }
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
                        .put("lastRead", e.lastRead),
                )
            }
            prefs(context, space).edit().putString("books", array.toString()).apply()
        }
    }

    fun upsert(context: Context, space: Int, key: String, uri: String, name: String, bytes: Int) {
        val current = load(context, space).toMutableList()
        current.removeAll { it.key == key }
        current.add(
            0,
            TxtShelfEntry(key, uri, name, bytes, System.currentTimeMillis()),
        )
        saveAll(context, space, current)
    }

    fun remove(context: Context, space: Int, key: String) {
        saveAll(context, space, load(context, space).filter { it.key != key })
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

/**
 * 书架页（fork.27）：当前空间的书目列表。点卡片继续读（带进度），
 * 长按移除书架记录；右上角/底部「添加书籍」走系统选择器多选加书。
 */
@Composable
fun BookshelfScreen(space: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(TxtBookshelfStore.load(context, space)) }
    var pendingRemove by remember { mutableStateOf<TxtShelfEntry?>(null) }

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

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "书架",
                subtitle = if (space == 1) "隐私空间" else "${entries.size} 本",
                navigationIcon = {
                    LiquidGlassBackButton(backdrop = null, surfaceVisible = true, onClick = onBack)
                },
                actions = {
                    TextButton(
                        text = "添加书籍",
                        onClick = { addLauncher.launch(arrayOf("text/plain")) },
                    )
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
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(entries.size) { index ->
                        val entry = entries[index]
                        val progress = remember(entry.key, entry.lastRead) {
                            TxtReaderStore.loadProgress(appContext, space, entry.key)
                        }
                        val percent = when {
                            progress == null || progress.total <= 0 -> null
                            else -> (progress.paragraph * 100 / progress.total).coerceIn(0, 100)
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                TxtReaderActivity.intent(
                                                    context, Uri.parse(entry.uri), entry.name, space,
                                                ),
                                            )
                                        }
                                    },
                                    onLongClick = { pendingRemove = entry },
                                ),
                            insideMargin = PaddingValues(16.dp),
                        ) {
                            Column {
                                Text(
                                    text = shelfTitle(entry.name),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = when {
                                            percent == null -> "未开始"
                                            percent >= 100 -> "已读完"
                                            else -> "已读 $percent%"
                                        },
                                        fontSize = 13.sp,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = if (entry.lastRead > 0) {
                                            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                                .format(Date(entry.lastRead))
                                        } else {
                                            ""
                                        },
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 移除确认（页内覆盖层）：只删书架记录，不删源文件
            pendingRemove?.let { entry ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .combinedClickable(onClick = { pendingRemove = null }),
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
                                text = "《${shelfTitle(entry.name)}》只会从书架列表里去掉，源文件和阅读进度都不删。",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row {
                                Spacer(Modifier.weight(1f))
                                TextButton(text = "取消", onClick = { pendingRemove = null })
                                Spacer(Modifier.padding(horizontal = 4.dp))
                                TextButton(
                                    text = "移除",
                                    onClick = {
                                        TxtBookshelfStore.remove(appContext, space, entry.key)
                                        entries = TxtBookshelfStore.load(appContext, space)
                                        pendingRemove = null
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
