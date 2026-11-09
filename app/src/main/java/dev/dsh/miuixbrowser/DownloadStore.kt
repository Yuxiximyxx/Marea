package dev.dsh.miuixbrowser

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 单个分段的持久化进度：[start, end] 是字节区间，[done] 是该段已写入的字节数 */
data class SegProgress(val start: Long, val end: Long, var done: Long)

/** 一个任务全部分段的进度表（分段下载续传的依据，与 .seg 临时文件配套保存） */
data class SegTable(val total: Long, val segments: MutableList<SegProgress>) {
    fun downloaded(): Long = segments.sumOf { it.done }
    fun complete(): Boolean = segments.all { it.done >= it.end - it.start + 1 }
}

/**
 * 下载任务持久化（fork 一期 P0）：任务元数据 + 分段进度表存 filesDir/mafari_downloads.json。
 *
 * 临时文件（.part / .seg）也改存 filesDir/mafari_dl/，不再放 cacheDir——
 * CacheCleaner 的冷启动清理会整表搬走 cacheDir，放在那里重启即丢续传进度。
 * Cookie 只随任务存本机文件、不打日志（Diag 脱敏要求）。
 */
object DownloadStore {

    private const val STORE_FILE = "mafari_downloads.json"
    private const val DL_DIR = "mafari_dl"

    fun dlDir(context: Context): File = File(context.filesDir, DL_DIR).apply { mkdirs() }

    fun partFile(context: Context, id: Long): File = File(dlDir(context), "mafari_dl_$id.part")

    fun segFile(context: Context, id: Long): File = File(dlDir(context), "mafari_dl_$id.seg")

    @Synchronized
    fun save(context: Context, tasks: List<DownloadTask>, segTables: Map<Long, SegTable>) {
        runCatching {
            val root = JSONObject()
            val arr = JSONArray()
            for (t in tasks) {
                val o = JSONObject()
                o.put("id", t.id)
                o.put("name", t.name)
                o.put("url", t.url)
                o.put("userAgent", t.userAgent ?: JSONObject.NULL)
                o.put("mime", t.mime ?: JSONObject.NULL)
                o.put("cookie", t.cookie ?: JSONObject.NULL)
                o.put("referer", t.referer ?: JSONObject.NULL)
                o.put("uri", t.uri?.toString() ?: JSONObject.NULL)
                o.put("bytes", t.bytes)
                o.put("total", t.total)
                o.put("state", t.state.name)
                o.put("error", t.error ?: JSONObject.NULL)
                o.put("createdAt", t.createdAt)
                o.put("etag", t.etag ?: JSONObject.NULL)
                o.put("lastModified", t.lastModified ?: JSONObject.NULL)
                o.put("space", t.space)
                segTables[t.id]?.let { table ->
                    val segs = JSONArray()
                    for (s in table.segments) {
                        segs.put(JSONObject().put("s", s.start).put("e", s.end).put("d", s.done))
                    }
                    o.put("segTotal", table.total)
                    o.put("segments", segs)
                }
                arr.put(o)
            }
            root.put("tasks", arr)
            val f = File(context.filesDir, STORE_FILE)
            val tmp = File(context.filesDir, "$STORE_FILE.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(f)) f.writeText(root.toString())
        }
    }

    fun load(context: Context): Pair<List<DownloadTask>, Map<Long, SegTable>> {
        val f = File(context.filesDir, STORE_FILE)
        if (!f.exists()) return emptyList<DownloadTask>() to emptyMap()
        return runCatching {
            val root = JSONObject(f.readText())
            val arr = root.optJSONArray("tasks") ?: JSONArray()
            val tasks = mutableListOf<DownloadTask>()
            val tables = mutableMapOf<Long, SegTable>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                fun str(key: String): String? = if (o.isNull(key)) null else o.optString(key).ifBlank { null }
                val id = o.getLong("id")
                val state = runCatching { DownloadState.valueOf(o.optString("state")) }.getOrDefault(DownloadState.FAILED)
                tasks += DownloadTask(
                    id = id,
                    name = o.optString("name"),
                    url = o.optString("url"),
                    userAgent = str("userAgent"),
                    mime = str("mime"),
                    cookie = str("cookie"),
                    referer = str("referer"),
                    uri = str("uri")?.let { Uri.parse(it) },
                    bytes = o.optLong("bytes"),
                    total = o.optLong("total"),
                    // 进程被杀时正在跑的任务恢复为已暂停，等用户手动继续
                    state = if (state == DownloadState.RUNNING) DownloadState.PAUSED else state,
                    error = str("error"),
                    createdAt = o.optLong("createdAt", 0L),
                    etag = str("etag"),
                    lastModified = str("lastModified"),
                    space = o.optInt("space", 0),
                )
                val segs = o.optJSONArray("segments")
                if (segs != null && segs.length() > 0) {
                    val list = mutableListOf<SegProgress>()
                    for (j in 0 until segs.length()) {
                        val s = segs.getJSONObject(j)
                        list += SegProgress(s.getLong("s"), s.getLong("e"), s.getLong("d"))
                    }
                    tables[id] = SegTable(o.optLong("segTotal"), list)
                }
            }
            tasks to tables
        }.getOrDefault(emptyList<DownloadTask>() to emptyMap())
    }
}
