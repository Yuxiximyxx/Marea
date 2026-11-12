package dev.dsh.miuixbrowser

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 阅读器 WebDAV 同步（fork.30）。
 *
 * 同步对象三类：书架、各书进度、每本书的目录规则选择——都以「空间:文件
 * 标识」为键放进服务器上的单个 JSON 文件（<服务器>/marea-reader/sync.json）。
 * 合并口径：逐条记录比时间戳，新者胜；设置类偏好不同步。目录规则选择
 * 按规则「名称」同步（规则 id 是本机生成的时间戳，跨设备无意义），合入
 * 时按名称在本机规则列表里找 id，找不到就跳过该条（记录仍留在合并文件
 * 里，其他设备可用）。书架删除不做墓碑传播：远端仍有的书会在同步后
 * 回到书架（v1 口径，设置页有说明）。
 *
 * 配置（地址 / 账号 / 密码）只存应用私有 SharedPreferences；密码不进
 * 日志、不进任何回报文本。网络失败静默，结果只落 lastResult 供设置页
 * 「同步」分区展示一行。
 */
object TxtSync {

    data class Config(val url: String, val user: String, val pass: String)

    private const val PREFS = "marea_txt_sync"
    private const val DIR_NAME = "marea-reader"
    private const val FILE_NAME = "sync.json"

    private val lock = Any()
    private var running = false

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun config(context: Context): Config? {
        val p = prefs(context)
        val url = p.getString("url", "")?.trim().orEmpty()
        if (url.isEmpty()) return null
        return Config(url, p.getString("user", "") ?: "", p.getString("pass", "") ?: "")
    }

    fun saveConfig(context: Context, url: String, user: String, pass: String) {
        prefs(context).edit()
            .putString("url", url.trim())
            .putString("user", user)
            .putString("pass", pass)
            .apply()
    }

    fun lastResult(context: Context): String? =
        prefs(context).getString("last_result", null)

    private fun setLastResult(context: Context, text: String) {
        prefs(context).edit().putString("last_result", text).apply()
    }

    /** 配了服务器才同步；给打开书籍 / 书架时的静默触发用，失败不打扰用户 */
    suspend fun syncIfConfigured(context: Context) {
        if (config(context) == null) return
        runCatching { sync(context) }
    }

    /**
     * 执行一次完整同步（拉取 → 逐条合并 → 应用远端胜出项 → 回传合并结果）。
     * 返回结果说明（同时落 lastResult）。拉取阶段网络失败直接中止，
     * 不拿本地数据覆盖远端。
     */
    suspend fun sync(context: Context): String {
        val cfg = config(context) ?: return "尚未配置服务器"
        synchronized(lock) {
            if (running) return lastResult(context) ?: "同步进行中"
            running = true
        }
        try {
            val result = withContext(Dispatchers.IO) { doSync(context, cfg) }
            setLastResult(context, result)
            return result
        } finally {
            synchronized(lock) { running = false }
        }
    }

    private fun doSync(context: Context, cfg: Config): String {
        val base = cfg.url.trimEnd('/')
        val dirUrl = "$base/$DIR_NAME"
        val fileUrl = "$dirUrl/$FILE_NAME"

        // 1) 拉远端（404 = 还没有同步文件，按空处理；其余错误中止）
        val remoteText: String? = try {
            val (code, body) = http(cfg, "GET", fileUrl, null)
            when (code) {
                200 -> body?.toString(Charsets.UTF_8)
                404 -> null
                401, 403 -> return "同步失败：认证被拒（HTTP $code），检查账号密码"
                else -> return "同步失败：服务器返回 HTTP $code"
            }
        } catch (e: FileNotFoundException) {
            null
        } catch (e: Exception) {
            return "同步失败：${briefError(e)}"
        }
        val remote = parsePayload(remoteText)

        // 2) 本地快照
        val local = localSnapshot(context)

        // 3) 逐类合并（时间戳新者胜）并把远端胜出项应用到本地
        val mergedBooks = mergeCategory(local.books, remote.books) { it.optLong("updatedAt", 0L) }
        applyBooks(context, mergedBooks.winners)
        val mergedProgress = mergeCategory(local.progress, remote.progress) { it.optLong("ts", 0L) }
        applyProgress(context, mergedProgress.winners)
        val mergedToc = mergeCategory(local.toc, remote.toc) { it.optLong("ts", 0L) }
        applyTocChoices(context, mergedToc.winners)

        // 4) 回传合并结果（先尽力建目录，MKCOL 失败不阻断——目录多半已存在）
        val payload = JSONObject()
            .put("version", 1)
            .put("books", mergedBooks.merged)
            .put("progress", mergedProgress.merged)
            .put("toc", mergedToc.merged)
            .toString()
        try {
            runCatching { http(cfg, "MKCOL", dirUrl, null) }
            val (code, _) = http(cfg, "PUT", fileUrl, payload.toByteArray(Charsets.UTF_8))
            if (code !in 200..299) return "同步失败：上传被拒（HTTP $code）"
        } catch (e: Exception) {
            return "同步失败：${briefError(e)}"
        }
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val bookCount = mergedBooks.merged.length()
        return "同步成功 $time · 书架 $bookCount 本"
    }

    private class Payload(
        val books: JSONObject,
        val progress: JSONObject,
        val toc: JSONObject,
    )

    private fun parsePayload(text: String?): Payload {
        if (text.isNullOrBlank()) {
            return Payload(JSONObject(), JSONObject(), JSONObject())
        }
        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: return Payload(JSONObject(), JSONObject(), JSONObject())
        return Payload(
            root.optJSONObject("books") ?: JSONObject(),
            root.optJSONObject("progress") ?: JSONObject(),
            root.optJSONObject("toc") ?: JSONObject(),
        )
    }

    private fun localSnapshot(context: Context): Payload {
        val books = JSONObject()
        val progress = JSONObject()
        val toc = JSONObject()
        val rules = TxtTocRules.load(context)
        for (space in 0..1) {
            for (entry in TxtBookshelfStore.load(context, space)) {
                books.put(
                    "$space:${entry.key}",
                    JSONObject()
                        .put("key", entry.key)
                        .put("space", space)
                        .put("uri", entry.uri)
                        .put("name", entry.name)
                        .put("bytes", entry.bytes)
                        .put("lastRead", entry.lastRead)
                        .put("addedAt", entry.addedAt)
                        .put("pinned", entry.pinned)
                        .put("updatedAt", entry.updatedAt),
                )
            }
            for (key in TxtReaderStore.progressKeys(context, space)) {
                val p = TxtReaderStore.loadProgress(context, space, key) ?: continue
                progress.put(
                    "$space:$key",
                    JSONObject()
                        .put("key", key)
                        .put("space", space)
                        .put("paragraph", p.paragraph)
                        .put("offsetPx", p.offsetPx)
                        .put("charOffset", p.charOffset)
                        .put("encoding", p.encoding ?: "")
                        .put("total", p.total)
                        .put("ts", TxtReaderStore.progressTs(context, space, key)),
                )
            }
            for (key in TxtReaderStore.tocChoiceKeys(context, space)) {
                val choiceId = TxtReaderStore.loadTocRuleChoice(context, space, key) ?: continue
                val ruleName = rules.firstOrNull { it.id == choiceId }?.name ?: continue
                toc.put(
                    "$space:$key",
                    JSONObject()
                        .put("key", key)
                        .put("space", space)
                        .put("ruleName", ruleName)
                        .put("ts", TxtReaderStore.tocTs(context, space, key)),
                )
            }
        }
        return Payload(books, progress, toc)
    }

    private class MergeOutcome(
        val merged: JSONObject,
        /** 键 → 胜出的远端记录（仅当远端比本地新时出现，供应用到本地） */
        val winners: Map<String, JSONObject>,
    )

    private fun mergeCategory(
        local: JSONObject,
        remote: JSONObject,
        tsOf: (JSONObject) -> Long,
    ): MergeOutcome {
        val merged = JSONObject()
        val winners = HashMap<String, JSONObject>()
        val keys = HashSet<String>()
        local.keys().forEach { keys += it }
        remote.keys().forEach { keys += it }
        for (key in keys) {
            val l = local.optJSONObject(key)
            val r = remote.optJSONObject(key)
            when {
                l == null && r != null -> {
                    merged.put(key, r)
                    winners[key] = r
                }
                r == null && l != null -> merged.put(key, l)
                l != null && r != null -> {
                    if (tsOf(r) > tsOf(l)) {
                        merged.put(key, r)
                        winners[key] = r
                    } else {
                        merged.put(key, l)
                    }
                }
            }
        }
        return MergeOutcome(merged, winners)
    }

    private fun applyBooks(context: Context, winners: Map<String, JSONObject>) {
        for ((_, record) in winners) {
            val key = record.optString("key")
            val uri = record.optString("uri")
            if (key.isBlank() || uri.isBlank()) continue
            val space = record.optInt("space", 0)
            val lastRead = record.optLong("lastRead", 0L)
            TxtBookshelfStore.mergeEntry(
                context,
                space,
                TxtShelfEntry(
                    key = key,
                    uri = uri,
                    name = record.optString("name").ifBlank { "TXT 文档" },
                    bytes = record.optInt("bytes", 0),
                    lastRead = lastRead,
                    addedAt = record.optLong("addedAt", lastRead),
                    pinned = record.optBoolean("pinned", false),
                    updatedAt = record.optLong("updatedAt", lastRead),
                ),
            )
        }
    }

    private fun applyProgress(context: Context, winners: Map<String, JSONObject>) {
        for ((_, record) in winners) {
            val key = record.optString("key")
            if (key.isBlank()) continue
            TxtReaderStore.saveProgressFromSync(
                context,
                record.optInt("space", 0),
                key,
                paragraph = record.optInt("paragraph", 0),
                offsetPx = record.optInt("offsetPx", 0),
                charOffset = record.optInt("charOffset", 0),
                encoding = record.optString("encoding").ifBlank { null },
                total = record.optInt("total", 0),
                ts = record.optLong("ts", 0L),
            )
        }
    }

    private fun applyTocChoices(context: Context, winners: Map<String, JSONObject>) {
        if (winners.isEmpty()) return
        val rules = TxtTocRules.load(context)
        for ((_, record) in winners) {
            val key = record.optString("key")
            val ruleName = record.optString("ruleName")
            if (key.isBlank() || ruleName.isBlank()) continue
            // 按名称找本机规则 id；本机没有这条规则就跳过，不动本地选择
            val localRule = rules.firstOrNull { it.name == ruleName } ?: continue
            TxtReaderStore.saveTocRuleChoiceFromSync(
                context,
                record.optInt("space", 0),
                key,
                localRule.id,
                record.optLong("ts", 0L),
            )
        }
    }

    /** 一次 HTTP 请求，返回 (状态码, 响应体)。认证头只在配了账号时带。 */
    private fun http(
        cfg: Config,
        method: String,
        urlStr: String,
        body: ByteArray?,
    ): Pair<Int, ByteArray?> {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            if (cfg.user.isNotEmpty()) {
                val raw = "${cfg.user}:${cfg.pass}".toByteArray(Charsets.UTF_8)
                conn.setRequestProperty(
                    "Authorization",
                    "Basic " + Base64.encodeToString(raw, Base64.NO_WRAP),
                )
            }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val data = runCatching { stream?.use { it.readBytes() } }.getOrNull()
            return code to data
        } finally {
            conn.disconnect()
        }
    }

    private fun briefError(e: Exception): String = when (e) {
        is UnknownHostException -> "找不到服务器，检查地址与网络"
        is SocketTimeoutException -> "连接超时"
        else -> "网络错误（${e.javaClass.simpleName}）"
    }
}
