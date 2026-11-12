package dev.dsh.miuixbrowser

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

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
 *
 * fork.31 修同步不可用：fork.30 用 HttpURLConnection 发 MKCOL——Android
 * 的 HttpURLConnection 实现只允许 GET/POST/PUT/DELETE 等标准方法，
 * setRequestMethod("MKCOL") 直接抛 ProtocolException，而旧代码把建目录
 * 调用包在 runCatching 里吞掉了异常，目录从来没建出来；首次同步时
 * PUT 到不存在的父目录，Koofr 回 404（多数服务器回 409）→「上传被拒
 * (HTTP 404)」。改用 OkHttp 发请求（任意方法可用），且建目录改为显式
 * 校验响应、PUT 遇 404/409 先建目录再重试一次；错误文案带环节。
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
        // base 规范化：去首尾空白与尾斜杠，再逐段拼目录 / 文件名，
        // 避免出现双斜杠或缺斜杠
        val base = cfg.url.trim().trimEnd('/')
        val dirUrl = "$base/$DIR_NAME"
        val fileUrl = "$dirUrl/$FILE_NAME"

        // 1) 拉远端（404 = 还没有同步文件，按空处理；其余错误中止，
        //    不拿本地数据覆盖远端）
        val remoteText: String? = try {
            val (code, body) = http(cfg, "GET", fileUrl, null)
            when (code) {
                200 -> body?.toString(Charsets.UTF_8)
                404 -> null
                401, 403 -> return "同步失败：认证被拒（HTTP $code），检查账号密码"
                else -> return "同步失败：拉取失败（HTTP $code）"
            }
        } catch (e: Exception) {
            return "同步失败：拉取失败（${briefError(e)}）"
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

        // 4) 回传合并结果：先建目录并验响应（目录不存在时 PUT 必被拒，
        //    Koofr 回 404、多数服务器回 409）；PUT 仍遇 404/409 时
        //    再建一次目录并重试一次
        val payload = JSONObject()
            .put("version", 1)
            .put("books", mergedBooks.merged)
            .put("progress", mergedProgress.merged)
            .put("toc", mergedToc.merged)
            .toString()
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        ensureCollection(cfg, dirUrl)?.let { return it }
        try {
            var (code, _) = http(cfg, "PUT", fileUrl, payloadBytes)
            if (code == 404 || code == 409) {
                ensureCollection(cfg, dirUrl)?.let { return it }
                val retry = http(cfg, "PUT", fileUrl, payloadBytes)
                code = retry.first
            }
            if (code !in 200..299) return "同步失败：上传被拒（HTTP $code）"
        } catch (e: Exception) {
            return "同步失败：上传失败（${briefError(e)}）"
        }
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val bookCount = mergedBooks.merged.length()
        return "同步成功 $time · 书架 $bookCount 本"
    }

    /**
     * 确保远端集合（同步目录）存在：MKCOL 并检查响应。
     * 201/200/204 = 已建好；405 = 集合已存在（mod_dav 等实现的惯例响应）。
     * 返回 null 表示通过，否则返回带环节的失败文案。
     */
    private fun ensureCollection(cfg: Config, dirUrl: String): String? {
        return try {
            val (code, _) = http(cfg, "MKCOL", dirUrl, ByteArray(0))
            when (code) {
                201, 200, 204, 405 -> null
                401, 403 -> "同步失败：建目录时认证被拒（HTTP $code），检查账号密码"
                else -> "同步失败：建目录失败（HTTP $code），检查服务器地址"
            }
        } catch (e: Exception) {
            "同步失败：建目录失败（${briefError(e)}）"
        }
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

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * 一次 HTTP 请求，返回 (状态码, 响应体)。认证头只在配了账号时带。
     * 用 OkHttp 而非 HttpURLConnection：后者在 Android 上不允许
     * MKCOL / PROPFIND 等 WebDAV 方法（setRequestMethod 抛
     * ProtocolException），fork.30 的建目录就是这样静默失败的。
     */
    private fun http(
        cfg: Config,
        method: String,
        urlStr: String,
        body: ByteArray?,
    ): Pair<Int, ByteArray?> {
        val builder = Request.Builder().url(urlStr)
        if (cfg.user.isNotEmpty()) {
            builder.header("Authorization", Credentials.basic(cfg.user, cfg.pass, Charsets.UTF_8))
        }
        when (method) {
            "GET" -> builder.get()
            "PUT" -> builder.put((body ?: ByteArray(0)).toRequestBody(jsonMediaType))
            else -> builder.method(method, body?.toRequestBody(jsonMediaType))
        }
        client.newCall(builder.build()).execute().use { response ->
            return response.code to response.body?.bytes()
        }
    }

    private fun briefError(e: Exception): String = when (e) {
        is UnknownHostException -> "找不到服务器，检查地址与网络"
        is SocketTimeoutException -> "连接超时"
        else -> "网络错误（${e.javaClass.simpleName}）"
    }
}
