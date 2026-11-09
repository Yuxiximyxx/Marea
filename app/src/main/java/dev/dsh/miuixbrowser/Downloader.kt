package dev.dsh.miuixbrowser

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.text.DecimalFormat
import java.util.concurrent.ConcurrentHashMap

enum class DownloadState { RUNNING, PAUSED, DONE, FAILED, QUEUED, RETRYING }

data class DownloadTask(
    val id: Long = System.nanoTime(),
    val name: String,
    val url: String,
    val userAgent: String? = null,
    val mime: String? = null,
    /** 下载时透传的 Cookie（来自 WebView CookieManager，登录态 / 防盗链站点必需），只存本机不打日志 */
    val cookie: String? = null,
    /** 下载时透传的来源页 Referer */
    val referer: String? = null,
    val uri: Uri? = null,
    val bytes: Long = 0,
    val total: Long = 0,
    val state: DownloadState = DownloadState.RUNNING,
    val error: String? = null,
    /** 近期下载速度（字节/秒，滑动平均；0 = 暂无样本），仅运行态有意义 */
    val speedBps: Long = 0,
    /** 任务级自动重试已进行的轮次（列表显示「重试中」用；成功或手动重试后清零） */
    val retryRound: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** 远端文件指纹：恢复续传前比对，变了就整单重下，避免新旧内容拼错 */
    val etag: String? = null,
    val lastModified: String? = null,
    /** 所属空间：0 = 普通，1 = 隐私（fork.5 隐私模式）。隐私任务只在隐私模式的下载列表里出现，文件落 Download/Marea/Private */
    val space: Int = 0,
)

/** 内置下载器：断点续传、暂停/继续、失败重试，默认写入 Download/Marea */
object Downloader {

    val tasks = mutableStateListOf<DownloadTask>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val numberFormat = DecimalFormat("0.0")

    /** 分段进度表（与 DownloadStore 落盘内容同源，恢复续传的依据） */
    private val segTables = ConcurrentHashMap<Long, SegTable>()

    /** HTTP 错误：可重试与否按状态码分——408/429/5xx 可重试，403/404/410 等直接失败 */
    private class HttpError(val code: Int) : Exception("HTTP $code") {
        val retryable: Boolean get() = code == 408 || code == 429 || code >= 500
    }

    /** 可重试的传输层错误（连接中断、分段未下完等，区别于服务器明确拒绝） */
    private class RetryableError(message: String) : Exception(message)

    /**
     * 速度采样器：按 (时刻, 累计字节) 滑动估算近期速度（字节/秒），
     * 指数平滑（新样本权重 1/4），样本间隔太短不更新，避免抖动。
     */
    private class SpeedTracker {
        private var lastTime = 0L
        private var lastBytes = 0L

        @Volatile
        var bps: Long = 0
            private set

        @Synchronized
        fun sample(bytes: Long) {
            val now = System.currentTimeMillis()
            if (lastTime == 0L) {
                lastTime = now
                lastBytes = bytes
                return
            }
            val dt = now - lastTime
            if (dt < 400) return
            val inst = ((bytes - lastBytes).coerceAtLeast(0) * 1000) / dt
            bps = if (bps <= 0) inst else (bps * 3 + inst) / 4
            lastTime = now
            lastBytes = bytes
        }
    }

    private val speedTrackers = ConcurrentHashMap<Long, SpeedTracker>()

    /** 任务当前速度（字节/秒）；非运行态或无样本为 0 */
    fun speedOf(task: DownloadTask): Long =
        if (task.state == DownloadState.RUNNING || task.state == DownloadState.RETRYING) {
            speedTrackers[task.id]?.bps?.takeIf { it > 0 } ?: task.speedBps
        } else {
            0L
        }

    /** 预计剩余秒数；速度或总量未知时返回 -1 */
    fun etaSeconds(task: DownloadTask): Long {
        val speed = speedOf(task)
        if (speed <= 0 || task.total <= 0 || task.bytes >= task.total) return -1
        return (task.total - task.bytes + speed - 1) / speed
    }

    fun etaText(seconds: Long): String = when {
        seconds < 60 -> "不足 1 分钟"
        seconds < 3600 -> "约 ${seconds / 60} 分钟"
        else -> "约 ${seconds / 3600} 小时 ${(seconds % 3600) / 60} 分"
    }

    /** 通知 / 列表共用的进度文案：百分比 · 已下载量 · 速度 · 预计剩余时间 */
    fun progressText(task: DownloadTask, written: Long, total: Long): String {
        val pct = if (total > 0) (written * 100 / total).toInt() else 0
        val sb = StringBuilder("$pct% · ${sizeText(written)}")
        val speed = speedOf(task)
        if (speed > 0) {
            sb.append(" · ${sizeText(speed)}/s")
            if (total > written) {
                val eta = (total - written + speed - 1) / speed
                sb.append(" · 剩余 ").append(etaText(eta))
            }
        }
        return sb.toString()
    }

    /** 列表项用的速度/剩余时间后缀（无速度样本时为空串） */
    fun speedEtaText(task: DownloadTask): String {
        val speed = speedOf(task)
        if (speed <= 0) return ""
        val eta = etaSeconds(task)
        return " · ${sizeText(speed)}/s" + if (eta >= 0) " · 剩余 ${etaText(eta)}" else ""
    }

    /** 同时进行的任务数上限（下载设置可改，默认 3）；超出上限的新任务进 QUEUED 排队 */
    @Volatile
    var maxConcurrent = 3

    private val queueLock = Object()
    private var activeCount = 0

    private fun tryAcquireSlot(): Boolean = synchronized(queueLock) {
        if (activeCount < maxConcurrent) {
            activeCount++
            true
        } else {
            false
        }
    }

    private fun releaseSlot() {
        synchronized(queueLock) { if (activeCount > 0) activeCount-- }
    }

    /**
     * 统一的「请求开始」入口（新建 / 继续 / 手动重试共用）：
     * 有空槽立即跑（槽位在 launch 的 finally 里释放并递补），没空槽落 QUEUED 排队并持久化。
     */
    private fun requestStart(context: Context, id: Long, freshRound: Boolean): Boolean {
        if (tryAcquireSlot()) {
            update(id) {
                it.copy(
                    state = DownloadState.RUNNING,
                    error = null,
                    retryRound = if (freshRound) 0 else it.retryRound,
                )
            }
            persistNow()
            DownloadService.start(context)
            tasks.firstOrNull { it.id == id }?.let { DownloadService.showStart(context, it.name, id) }
            launch(context, id)
            return true
        }
        update(id) { it.copy(state = DownloadState.QUEUED, error = null) }
        persistNow()
        DownloadService.start(context)
        tasks.firstOrNull { it.id == id }?.let { DownloadService.showQueued(context, it.name, id) }
        Diag.log("Downloader: task $id queued (maxConcurrent=$maxConcurrent)")
        return false
    }

    /** 槽位空出来后递补：按创建顺序取最早的 QUEUED 任务，直到槽位占满或队列空 */
    private fun pumpQueue(context: Context) {
        while (true) {
            val next = synchronized(queueLock) {
                if (activeCount >= maxConcurrent) return
                val candidate = tasks.filter { it.state == DownloadState.QUEUED }
                    .minByOrNull { it.createdAt } ?: return
                activeCount++
                candidate
            }
            update(next.id) { it.copy(state = DownloadState.RUNNING, error = null) }
            persistNow()
            DownloadService.start(context)
            DownloadService.showStart(context, next.name, next.id)
            launch(context, next.id)
        }
    }

    /** 设置里改了同时下载数：更新上限并立刻递补（调大时等待任务马上开跑） */
    fun applyMaxConcurrent(context: Context, value: Int) {
        maxConcurrent = value.coerceAtLeast(1)
        pumpQueue(context)
    }

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var restored = false

    @Volatile
    private var lastPersistAt = 0L

    /**
     * 启动时恢复落盘的任务（MainActivity / WebAppActivity 各调一次，幂等）。
     * 进程被杀时处于 RUNNING 的任务在落盘读取时已转成 PAUSED，用户手动继续即可续传。
     */
    fun restore(context: Context) {
        if (restored) return
        restored = true
        appContext = context.applicationContext
        val (saved, tables) = DownloadStore.load(appContext!!)
        segTables.putAll(tables)
        if (saved.isNotEmpty() && tasks.isEmpty()) {
            tasks.addAll(saved.sortedByDescending { it.createdAt })
        }
    }

    /** 立即落盘（生命周期事件用：新建 / 暂停 / 失败 / 完成 / 删除） */
    private fun persistNow() {
        val ctx = appContext ?: return
        lastPersistAt = System.currentTimeMillis()
        DownloadStore.save(ctx, tasks.toList(), segTables)
    }

    /** 节流落盘（进度更新用，最多每秒一次） */
    private fun persistThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastPersistAt < 1000L) return
        persistNow()
    }

    private fun bindContext(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun fileNameFrom(url: String, disposition: String?): String {
        val raw = disposition?.let { d ->
            Regex("filename\\*?=(?:UTF-8''|\")?([^\";]+)").find(d)?.groupValues?.get(1)?.trim()
        } ?: runCatching { Uri.parse(url).lastPathSegment }.getOrNull()
        val name = runCatching { URLDecoder.decode(raw ?: "", "UTF-8") }
            .getOrDefault(raw ?: "")
            .ifBlank { "download_${System.currentTimeMillis()}" }
        return truncateName(name.replace(Regex("[\\\\/:*?\"<>|]"), "_"), 80)
    }

    /**
     * 页面给定的原始文件名（<a download> / File.name）：原样保留，
     * 只去掉路径分隔符和控制字符（系统不允许），不补扩展名、不替换其它字符。
     */
    fun originalName(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/\\u0000-\\u001F]"), "_").trim()
        return truncateName(cleaned.ifBlank { "download_${System.currentTimeMillis()}" }, 255)
    }

    /** 超长文件名截断时保留扩展名 */
    private fun truncateName(name: String, max: Int): String {
        if (name.length <= max) return name
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0 && name.length - dot <= 10) name.substring(dot) else ""
        return name.substring(0, (max - ext.length).coerceAtLeast(1)) + ext
    }

    /** 文件名没有扩展名时，按 MIME 补一个（blob 地址的文件名常常只是一串 UUID） */
    fun withExtension(name: String, mime: String?): String {
        if (Regex("\\.[A-Za-z0-9]{1,8}$").containsMatchIn(name)) return name
        val type = mime?.substringBefore(';')?.trim()?.lowercase()
        if (type.isNullOrBlank() || type == "application/octet-stream") return name
        val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
        return if (ext.isNullOrBlank()) name else truncateName("$name.$ext", 80)
    }

    fun start(
        context: Context,
        url: String,
        userAgent: String?,
        fileName: String,
        mimeType: String?,
        cookie: String? = null,
        referer: String? = null,
        space: Int = 0,
    ) {
        bindContext(context)
        val task = DownloadTask(
            name = fileName,
            url = url,
            userAgent = userAgent,
            mime = mimeType,
            cookie = cookie,
            referer = referer,
            space = space,
        )
        tasks.add(0, task)
        persistNow()
        requestStart(context, task.id, freshRound = true)
    }

    /** 只建任务不启动（fork.5 新建下载弹窗的「添加」）：PAUSED 落盘，进下载列表等用户手动开始。调用前须先 restore 过。 */
    fun addPaused(
        context: Context,
        url: String,
        userAgent: String?,
        fileName: String,
        mimeType: String?,
        cookie: String? = null,
        referer: String? = null,
        space: Int = 0,
    ) {
        bindContext(context)
        val task = DownloadTask(
            name = fileName,
            url = url,
            userAgent = userAgent,
            mime = mimeType,
            cookie = cookie,
            referer = referer,
            state = DownloadState.PAUSED,
            space = space,
        )
        tasks.add(0, task)
        persistNow()
    }

    /** 已知下载主机：这些域名的链接基本都是文件直链（签名 URL 常没有扩展名） */
    private val DOWNLOAD_HOSTS = setOf(
        "release-assets.githubusercontent.com",
        "objects.githubusercontent.com",
        "codeload.github.com",
    )

    /** 常见可下载文件扩展名（外部链接分诊用：命中即认为是直链下载） */
    private val FILE_EXTENSIONS = setOf(
        "apk", "xapk", "apks", "apkm", "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "epub",
        "mp3", "flac", "wav", "ogg", "m4a", "mp4", "mkv", "avi", "mov", "webm",
        "iso", "img", "dmg", "exe", "msi", "bin", "torrent", "crx", "ipa", "deb", "rpm",
    )

    /**
     * 外部链接分诊：仅凭 URL 形态判断是否直链下载（不发网络请求），判定克制——
     * 只认三类特征：①已知下载主机（签名直链基本都在这几家）；②路径最后一段带文件扩展名；
     * ③查询参数里带文件名特征（filename=xxx.apk、response-content-disposition 里的文件名，
     * 或某个参数值以文件扩展名结尾）。普通网页链接一律不命中。
     */
    fun looksLikeFileUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (host in DOWNLOAD_HOSTS) return true
        val segment = uri.path?.substringAfterLast('/') ?: ""
        val ext = segment.substringAfterLast('.', "").lowercase()
        if (ext in FILE_EXTENSIONS) return true
        val query = runCatching { URLDecoder.decode(uri.query ?: "", "UTF-8") }.getOrNull().orEmpty()
        if (query.isNotBlank()) {
            val named = Regex("""filename\*?\s*=\s*"?([^";\s]+)""").find(query)?.groupValues?.get(1)
            if (named != null && named.substringAfterLast('.', "").lowercase() in FILE_EXTENSIONS) return true
            val extAlt = FILE_EXTENSIONS.joinToString("|")
            if (Regex("""[?&#][^&#\s]*\.($extAlt)(?:$|[&#])""").containsMatchIn(url.lowercase())) return true
        }
        return false
    }

    /**
     * 探测结果：[size] < 0 表示未知；[fileName] 是 Content-Disposition 推导名（可空）；
     * [finalUrl] 是跟随重定向后的最终地址（fork.7：弹窗据此回填，文件名与下载都以它为准）。
     */
    data class ProbeResult(val size: Long, val fileName: String?, val mime: String?, val finalUrl: String? = null)

    /**
     * 轻量探测（fork.5 新建下载弹窗）：先 HEAD（跟随重定向），拿不到长度或 HEAD 被拒时
     * 用 Range GET bytes=0-0 兜底，从 Content-Range 读总长；读完响应头即断开、不下正文。
     * 带 UA / Cookie 保证登录态站点也能探到。
     */
    suspend fun probe(url: String, userAgent: String?, cookie: String?): ProbeResult =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching {
                val head = openProbe(url, userAgent, cookie, "HEAD")
                val headCode = head.responseCode
                var size = head.contentLengthLong
                var disposition = head.getHeaderField("Content-Disposition")
                var mime = head.contentType?.substringBefore(';')
                var finalUrl = runCatching { head.url?.toString() }.getOrNull()
                head.disconnect()
                if (size < 0 || headCode >= 400) {
                    val ranged = openProbe(url, userAgent, cookie, "GET", range = true)
                    ranged.responseCode
                    size = ranged.getHeaderField("Content-Range")
                        ?.substringAfterLast('/')?.toLongOrNull()
                        ?: ranged.contentLengthLong
                    if (disposition == null) disposition = ranged.getHeaderField("Content-Disposition")
                    if (mime == null) mime = ranged.contentType?.substringBefore(';')
                    finalUrl = runCatching { ranged.url?.toString() }.getOrNull() ?: finalUrl
                    ranged.disconnect()
                }
                ProbeResult(
                    size = size,
                    fileName = disposition?.let { fileNameFrom(finalUrl ?: url, it) },
                    mime = mime,
                    finalUrl = finalUrl,
                )
            }.getOrDefault(ProbeResult(-1L, null, null))
        }

    private fun openProbe(url: String, userAgent: String?, cookie: String?, method: String, range: Boolean = false): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.requestMethod = method
        conn.setRequestProperty("User-Agent", userAgent ?: "Mozilla/5.0 mafari")
        if (!cookie.isNullOrBlank()) conn.setRequestProperty("Cookie", cookie)
        if (range) conn.setRequestProperty("Range", "bytes=0-0")
        return conn
    }

    fun pause(id: Long) {
        jobs.remove(id)?.cancel()
        update(id) { it.copy(state = DownloadState.PAUSED) }
        persistNow()
        val task = tasks.firstOrNull { it.id == id }
        if (task != null) {
            val progress = progressOf(task)
            appContext?.let { DownloadService.updatePaused(it, task.name, progress, id) }
        }
        appContext?.let { pumpQueue(it) }
    }

    fun resume(context: Context, id: Long) {
        bindContext(context)
        val state = tasks.firstOrNull { it.id == id }?.state ?: return
        // 已在跑 / 已在自动重试 / 已在排队的不要重复占槽
        if (state == DownloadState.RUNNING || state == DownloadState.RETRYING || state == DownloadState.QUEUED) return
        requestStart(context, id, freshRound = false)
    }

    fun retry(context: Context, id: Long) {
        bindContext(context)
        speedTrackers.remove(id)
        requestStart(context, id, freshRound = true)
    }

    /** 只有用户主动删除任务才清临时文件与分段进度；暂停 / 失败一律保留以便续传 */
    fun delete(context: Context, id: Long) {
        jobs.remove(id)?.cancel()
        speedTrackers.remove(id)
        val task = tasks.firstOrNull { it.id == id }
        task?.uri?.let { uri -> runCatching { context.contentResolver.delete(uri, null, null) } }
        runCatching { tempFile(context, id).delete() }
        runCatching { segFile(context, id).delete() }
        segTables.remove(id)
        tasks.removeAll { it.id == id }
        bindContext(context)
        persistNow()
        DownloadService.dismiss(context, id)
    }

    /** 临时文件放 filesDir（见 DownloadStore 说明），cacheDir 会被冷启动清理整表搬走 */
    private fun tempFile(context: Context, id: Long) = DownloadStore.partFile(context, id)

    /** 多线程分段下载专用临时文件：会预先撑满到总长度，不能和断点续传共用 .part，否则重试时会误当成“已下完”而发出越界 Range → HTTP 416 */
    private fun segFile(context: Context, id: Long) = DownloadStore.segFile(context, id)

    /** 下载在 IO 线程里完成，Toast 必须切回主线程 */
    private fun toast(context: Context, message: String) {
        val app = context.applicationContext
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(app, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 任务执行协程：槽位由 requestStart / pumpQueue 占好后调用；
     * 无论成败，finally 里都释放槽位并递补队列（暂停 / 删除引发的取消同样走这里）。
     */
    private fun launch(context: Context, id: Long) {
        bindContext(context)
        jobs.remove(id)?.cancel()
        jobs[id] = scope.launch {
            try {
                runTaskWithRetry(context, id)
            } finally {
                jobs.remove(id)
                speedTrackers.remove(id)
                releaseSlot()
                appContext?.let { pumpQueue(it) }
            }
        }
    }

    /**
     * 任务级执行 + 自动重试：分段级重试在 downloadSegmented 内部，这里管整单重试——
     * 可重试错误（网络类 IOException、5xx / 429 / 408）按 2s / 5s / 15s 退避、最多
     * [MAX_AUTO_ROUNDS] 轮，期间状态为 RETRYING（列表与通知都可见）；
     * 不可重试的 HTTP 错误（403 / 404 / 410 等）直接失败并写明原因，不空转重试。
     */
    private suspend fun runTaskWithRetry(context: Context, id: Long) {
        var round = 0
        while (true) {
            try {
                downloadOnce(context, id)
                val finished = tasks.firstOrNull { it.id == id }
                segTables.remove(id)
                update(id) { it.copy(state = DownloadState.DONE, retryRound = 0, speedBps = 0) }
                persistNow()
                DownloadService.finish(context, finished?.name ?: "文件", id)
                toast(context, "下载完成：${finished?.name ?: "文件"}")
                return
            } catch (e: CancellationException) {
                // 暂停 / 删除导致取消：分段进度已在各段写盘节流保存，这里再补一次确保不丢
                persistNow()
                throw e
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                val retryable = if (e is HttpError) e.retryable else true
                if (!retryable || round >= MAX_AUTO_ROUNDS) {
                    update(id) { it.copy(state = DownloadState.FAILED, error = message) }
                    persistNow()
                    DownloadService.fail(context, "下载失败：$message", id)
                    return
                }
                round++
                Diag.log("Downloader: task $id auto-retry round $round after: $message")
                update(id) { it.copy(state = DownloadState.RETRYING, error = message, retryRound = round) }
                persistNow()
                tasks.firstOrNull { it.id == id }?.let { t ->
                    DownloadService.update(context, t.name, progressOf(t), "重试中（第 $round 次）· $message", id)
                }
                delay(RETRY_BACKOFF_MS[(round - 1).coerceIn(0, RETRY_BACKOFF_MS.lastIndex)])
            }
        }
    }

    /** 所有下载请求统一带头：UA / Accept / Cookie / Referer（登录态与防盗链站点必需），Range 按需另加 */
    private fun HttpURLConnection.applyTaskHeaders(task: DownloadTask) {
        setRequestProperty("User-Agent", task.userAgent ?: "Mozilla/5.0 mafari")
        setRequestProperty("Accept", "*/*")
        setRequestProperty("Accept-Encoding", "identity")
        if (!task.cookie.isNullOrBlank()) setRequestProperty("Cookie", task.cookie)
        if (!task.referer.isNullOrBlank()) setRequestProperty("Referer", task.referer)
    }

    private suspend fun downloadOnce(context: Context, id: Long) {
        val task = tasks.firstOrNull { it.id == id } ?: return
        val file = tempFile(context, id)
        var existing = if (file.exists()) file.length() else 0L

        // 旧版本遗留的 / 已经完整的临时文件：长度已达总长度，再发 Range 必然 416，直接重来
        if (existing > 0 && task.total > 0 && existing >= task.total) {
            runCatching { file.delete() }
            existing = 0
        }

        fun open(from: Long): HttpURLConnection = (URL(task.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            applyTaskHeaders(task)
            if (from > 0) setRequestProperty("Range", "bytes=$from-")
        }

        var connection = open(existing)
        try {
            var code = connection.responseCode
            if (code == 416 && existing > 0) {
                // 服务器认为续传位置越界（文件已变 / 临时文件已满）：丢弃临时文件，从头下载
                connection.disconnect()
                runCatching { file.delete() }
                existing = 0
                connection = open(0)
                code = connection.responseCode
            }
            if (code !in 200..299) throw HttpError(code)
            val length = connection.contentLengthLong.coerceAtLeast(0)
            val append = existing > 0 && code == 206
            if (!append) {
                existing = 0
                runCatching { file.delete() }
            }
            // 记住远端指纹，分段续传恢复时比对（单线程路径靠 Range/416 兜底，这里只记录）
            val respEtag = connection.getHeaderField("ETag")
            val respLastModified = connection.getHeaderField("Last-Modified")
            if (respEtag != task.etag || respLastModified != task.lastModified) {
                update(id) { it.copy(etag = respEtag ?: it.etag, lastModified = respLastModified ?: it.lastModified) }
            }
            val liveTask = tasks.firstOrNull { it.id == id } ?: task
            update(id) {
                it.copy(
                    bytes = existing,
                    total = if (append) existing + length else length,
                    state = DownloadState.RUNNING,
                )
            }
            // 本次连接应下满的总量（0 = 长度未知，不做完整性判定）
            val knownTotal = if (append) existing + length else length
            // 多线程分段下载（服务器支持 Range 且文件 > 1MB 时）
            if (existing == 0L && append.not() && length > segmentMinBytes) {
                // 先放掉这条整文件连接，再探测 Range，避免对镜像同时开 N+2 条连接被限流
                connection.disconnect()
                val probe = probeRange(liveTask)
                if (probe.supported) {
                    val seg = segFile(context, id)
                    val table = prepareSegTable(id, liveTask, seg, length, probe)
                    downloadSegmented(context, id, liveTask, seg, length, table)
                    val result = finishToMediaStore(context, id, liveTask, seg)
                    segTables.remove(id)
                    persistNow()
                    return result
                }
                // 不支持 Range：重新建立单连接顺序下载
                connection = open(0)
                val retryCode = connection.responseCode
                if (retryCode !in 200..299) throw HttpError(retryCode)
            }

            connection.inputStream.use { input ->
                java.io.FileOutputStream(file, append).use { out ->
                    val buffer = ByteArray(256 * 1024)
                    var written = existing
                    var lastUpdate = 0L
                    val tracker = speedTrackers.getOrPut(id) { SpeedTracker() }
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 250) {
                            lastUpdate = now
                            tracker.sample(written)
                            update(id) { it.copy(bytes = written, speedBps = tracker.bps) }
                            val t = tasks.firstOrNull { it.id == id }
                            val total = t?.total ?: 0L
                            val progress = if (total > 0) written.toFloat() / total else 0f
                            DownloadService.update(
                                context,
                                task.name,
                                progress,
                                if (t != null) {
                                    progressText(t, written, total)
                                } else {
                                    "${(progress * 100).toInt()}% · ${sizeText(written)}"
                                },
                                id,
                            )
                        }
                    }
                    out.flush()
                    out.fd.sync()
                    update(id) { it.copy(bytes = written) }
                    // 服务器提前断流（长度已知却没下满）：按可重试错误处理，靠续传补齐，不拼残缺文件
                    if (knownTotal > 0 && written < knownTotal) {
                        throw RetryableError("连接中断，下载不完整")
                    }
                }
            }
            // 下载完成后一次性入库到 /sdcard/Download/Marea
            val uri = createEntry(context, task) ?: throw IllegalStateException("无法创建文件")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                    }
                }
            }
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
            update(id) { it.copy(uri = uri) }
            runCatching { file.delete() }
        } finally {
            connection.disconnect()
        }
    }

    private class RangeProbe(val supported: Boolean, val etag: String?, val lastModified: String?)

    /** Range 探测：bytes=0-0 得 206 才算支持，顺便带回远端指纹供续传比对 */
    private fun probeRange(task: DownloadTask): RangeProbe = runCatching {
        val head = (URL(task.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            instanceFollowRedirects = true
            applyTaskHeaders(task)
            setRequestProperty("Range", "bytes=0-0")
        }
        val code = head.responseCode
        val etag = head.getHeaderField("ETag")
        val lastModified = head.getHeaderField("Last-Modified")
        head.disconnect()
        RangeProbe(code == 206, etag, lastModified)
    }.getOrDefault(RangeProbe(false, null, null))

    /**
     * 准备分段进度表：已有落盘表 + .seg 文件长度吻合 + 远端指纹未变 → 沿用旧表续传；
     * 否则删掉旧 .seg、新建一张按当前线程数均分的表。指纹双方都缺失时视为未变（只能靠总长度兜底）。
     */
    private fun prepareSegTable(
        id: Long,
        task: DownloadTask,
        seg: java.io.File,
        total: Long,
        probe: RangeProbe,
    ): SegTable {
        val fingerprintChanged = run {
            val oldEtag = task.etag
            val oldLm = task.lastModified
            (oldEtag != null && probe.etag != null && oldEtag != probe.etag) ||
                (oldEtag == null && oldLm != null && probe.lastModified != null && oldLm != probe.lastModified)
        }
        if (fingerprintChanged) {
            update(id) { it.copy(etag = probe.etag ?: it.etag, lastModified = probe.lastModified ?: it.lastModified) }
        }
        val existing = segTables[id]
        if (existing != null && existing.total == total && seg.exists() && seg.length() == total && !fingerprintChanged) {
            return existing
        }
        runCatching { seg.delete() }
        // 初始分段数按文件大小分档（小文件少段、大文件多段），再受设置线程数与「每段至少 256KB」约束；
        // 下载中还有分段分裂兜底（慢段会被空闲 worker 劈开），初始档位不必激进
        val bySize = when {
            total < 8L * 1024 * 1024 -> 1
            total < 32L * 1024 * 1024 -> 2
            total < 128L * 1024 * 1024 -> 4
            total < 512L * 1024 * 1024 -> 8
            else -> 16
        }
        val threads = minOf(segments, bySize, ((total / (256 * 1024)) + 1).toInt()).coerceAtLeast(1)
        val chunk = total / threads
        val list = (0 until threads).map { index ->
            val start = index * chunk
            val end = if (index == threads - 1) total - 1 else start + chunk - 1
            SegProgress(start, end, 0L)
        }.toMutableList()
        val table = SegTable(total, list)
        segTables[id] = table
        persistNow()
        return table
    }

    /**
     * 分段下载（可续传版）：每段只下自己未完成的部分，段内失败只重试该段（3 次）；
     * 暂停 / 失败 / 进程被杀都不删 .seg 与进度表，恢复时从落盘表接着下。
     */
    private suspend fun downloadSegmented(
        context: Context,
        id: Long,
        task: DownloadTask,
        file: java.io.File,
        total: Long,
        table: SegTable,
    ) {
        java.io.RandomAccessFile(file, "rw").use { raf ->
            if (file.length() != total) raf.setLength(total)
            var lastUpdate = 0L
            fun reportProgress() {
                val now = System.currentTimeMillis()
                if (now - lastUpdate < 300) return
                lastUpdate = now
                val written = table.downloaded()
                val tracker = speedTrackers.getOrPut(id) { SpeedTracker() }
                tracker.sample(written)
                update(id) {
                    it.copy(bytes = written, total = total, state = DownloadState.RUNNING, speedBps = tracker.bps)
                }
                // 分段表 → ProgressStyle 分段：各段按字节占比换算成点数（合计 100，漂移并到最大段），
                // 已下完的段边界记为 points；段太多（>50）时不映射，实况通知退回整条进度
                val segCount = table.segments.size
                var segPoints: List<Int>? = null
                var segMarks: List<Int>? = null
                if (segCount in 2..50) {
                    val sizes = table.segments
                        .map { s -> (((s.end - s.start + 1) * 100) / total).toInt().coerceAtLeast(1) }
                        .toMutableList()
                    val drift = 100 - sizes.sum()
                    if (drift != 0) {
                        val maxIdx = sizes.indices.maxByOrNull { sizes[it] } ?: 0
                        sizes[maxIdx] = (sizes[maxIdx] + drift).coerceAtLeast(1)
                    }
                    var acc = 0
                    val marks = ArrayList<Int>()
                    table.segments.forEachIndexed { i, s ->
                        acc += sizes[i]
                        if (s.done >= s.end - s.start + 1 && acc in 1..99) marks.add(acc)
                    }
                    segPoints = sizes
                    segMarks = marks
                }
                DownloadService.update(
                    context,
                    task.name,
                    (written.toFloat() / total).coerceIn(0f, 1f),
                    progressText(tasks.firstOrNull { it.id == id } ?: task, written, total),
                    id,
                    segPoints,
                    segMarks,
                )
            }
            val workerError = java.util.concurrent.atomic.AtomicReference<Exception?>(null)
            coroutineScope {
                val cs = this
                val workers = ArrayList<Job>()
                fun spawnWorker(seg: SegProgress) {
                    workers += cs.launch(Dispatchers.IO) {
                        runSegmentWorker(task, raf, seg, workerError) { reportProgress() }
                    }
                }
                table.segments.toList().forEach { spawnWorker(it) }
                // 分段分裂（work-stealing 式）：盯着 worker 池，有空位时把剩余量最大的未完成段
                // 从中点劈成两半——慢段不再拖住整单。劈分只改内存表（.seg 按偏移写入、无需迁移），
                // 落盘节流会把新表存下，续传直接沿用（表格式不变）。
                while (workers.any { it.isActive }) {
                    delay(700)
                    val active = workers.count { it.isActive }
                    if (active == 0) break
                    if (active >= segments || table.segments.size >= 64) continue
                    val victim = synchronized(table) {
                        table.segments
                            .filter { it.end - it.start + 1 - it.done >= SPLIT_MIN_REMAINING }
                            .maxByOrNull { it.end - it.start + 1 - it.done }
                    } ?: continue
                    val spawned = synchronized(table) {
                        val remaining = victim.end - victim.start + 1 - victim.done
                        if (remaining < SPLIT_MIN_REMAINING) {
                            null
                        } else {
                            val mid = victim.start + victim.done + remaining / 2
                            val newSeg = SegProgress(mid, victim.end, 0L)
                            victim.end = mid - 1
                            table.segments.add(newSeg)
                            newSeg
                        }
                    } ?: continue
                    persistThrottled()
                    spawnWorker(spawned)
                }
                workers.forEach { it.join() }
            }
            // worker 记录的首个错误优先抛出（如 403 直接失败）；没错误但没下满 = 可重试的未完成
            workerError.get()?.let { throw it }
            if (!table.complete()) throw RetryableError("分段未全部完成")
            update(id) { it.copy(bytes = total, total = total) }
            persistNow()
        }
    }

    /**
     * 单个分段的 worker：只下该段剩余部分；段内失败先在本段重试 [MAX_ATTEMPTS] 次，
     * 仍失败则把错误记进 [workerError] 后退出（整单是重试还是失败由调用方统一决定）。
     * 分段可能在运行中被分裂缩小（end 变小），每轮都按当前边界重新判断是否完成。
     */
    private suspend fun runSegmentWorker(
        task: DownloadTask,
        raf: java.io.RandomAccessFile,
        seg: SegProgress,
        workerError: java.util.concurrent.atomic.AtomicReference<Exception?>,
        onProgress: () -> Unit,
    ) {
        var attempt = 0
        while (true) {
            val segSize = seg.end - seg.start + 1
            if (seg.done >= segSize) return
            try {
                downloadSegmentRange(task, raf, seg, onProgress)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is HttpError && !e.retryable) {
                    workerError.compareAndSet(null, e)
                    return
                }
                attempt++
                if (attempt >= MAX_ATTEMPTS) {
                    workerError.compareAndSet(null, e)
                    return
                }
                delay(1200L * attempt)
            }
        }
    }

    /** 下载单段剩余部分；每写一块就更新进度表并节流落盘 */
    private fun downloadSegmentRange(
        task: DownloadTask,
        raf: java.io.RandomAccessFile,
        seg: SegProgress,
        onProgress: () -> Unit,
    ) {
        val from = seg.start + seg.done
        val connection = (URL(task.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            applyTaskHeaders(task)
            setRequestProperty("Range", "bytes=$from-${seg.end}")
        }
        try {
            val code = connection.responseCode
            // 必须是 206：返回 200 说明服务器忽略了 Range，整文件会被写到错误的偏移上
            if (code != 206) {
                if (code in 200..299) throw RetryableError("服务器未按 Range 返回（HTTP $code）")
                throw HttpError(code)
            }
            connection.inputStream.use { input ->
                val buffer = ByteArray(128 * 1024)
                var position = from
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    synchronized(raf) {
                        raf.seek(position)
                        raf.write(buffer, 0, read)
                    }
                    position += read
                    seg.done = position - seg.start
                    persistThrottled()
                    onProgress()
                }
                if (position != seg.end + 1) {
                    throw IllegalStateException("分段数据不完整")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun finishToMediaStore(context: Context, id: Long, task: DownloadTask, file: java.io.File) {
        val uri = createEntry(context, task) ?: throw IllegalStateException("无法创建文件")
        context.contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { input ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
            }
        }
        context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null,
        )
        // 本地已有同名文件时系统会自动改名（如 xxx (1).zip），读回实际文件名
        update(id) { it.copy(uri = uri, name = actualName(context, uri, it.name)) }
        runCatching { file.delete() }
    }

    private fun createEntry(context: Context, task: DownloadTask): Uri? = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, task.name)
            put(MediaStore.Downloads.MIME_TYPE, task.mime ?: "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, if (task.space == 1) "Download/Marea/Private" else "Download/Marea")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }.getOrNull()

    /** 读取 MediaStore 实际落盘的显示名；同名文件已存在时系统会自动追加 (1) 等后缀 */
    private fun actualName(context: Context, uri: Uri, fallback: String): String = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
        }
    }.getOrNull() ?: fallback

    private fun update(id: Long, transform: (DownloadTask) -> DownloadTask) {
        val index = tasks.indexOfFirst { it.id == id }
        if (index >= 0) {
            tasks[index] = transform(tasks[index])
            persistThrottled()
        }
    }

    /** 保存 data:...;base64 的内嵌 / blob 抽取结果到系统下载目录 */
    fun saveDataUri(context: Context, dataUri: String, fileName: String, space: Int = 0): Boolean = runCatching {
        val comma = dataUri.indexOf(',')
        if (comma < 0) return@runCatching false
        val meta = dataUri.substringAfter("data:").substringBefore(',')
        if (!meta.contains("base64", ignoreCase = true)) return@runCatching false
        val bytes = android.util.Base64.decode(dataUri.substring(comma + 1), android.util.Base64.DEFAULT)
        saveBytes(context, bytes, fileName, meta.substringBefore(';').ifBlank { null }, space)
        true
    }.getOrDefault(false)

    /** 把缓存文件流式复制到 Download/Marea（大 blob 用，不把整个文件读进内存） */
    fun saveFile(context: Context, file: java.io.File, fileName: String, mime: String?, space: Int = 0): Boolean {
        var uri: Uri? = null
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime ?: "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, if (space == 1) "Download/Marea/Private" else "Download/Marea")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val target = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching false
            uri = target
            val out = context.contentResolver.openOutputStream(target) ?: error("no output stream")
            out.use { o -> file.inputStream().use { it.copyTo(o, 64 * 1024) } }
            context.contentResolver.update(
                target,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
            val size = file.length()
            tasks.add(
                0,
                DownloadTask(
                    name = actualName(context, target, fileName),
                    url = "",
                    mime = mime,
                    uri = target,
                    bytes = size,
                    total = size,
                    state = DownloadState.DONE,
                ),
            )
            bindContext(context)
            persistNow()
            true
        }.getOrElse {
            // 写到一半失败：删掉未完成的条目，避免留下残缺文件
            uri?.let { u -> runCatching { context.contentResolver.delete(u, null, null) } }
            false
        }
    }

    /** 直接把字节写入 Download/Marea（blob / data URL 共用） */
    fun saveBytes(context: Context, bytes: ByteArray, fileName: String, mime: String?, space: Int = 0): Boolean = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mime ?: "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, if (space == 1) "Download/Marea/Private" else "Download/Marea")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching false
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null,
        )
        tasks.add(
            0,
            DownloadTask(
                name = actualName(context, uri, fileName),
                url = "",
                mime = mime,
                uri = uri,
                bytes = bytes.size.toLong(),
                total = bytes.size.toLong(),
                state = DownloadState.DONE,
            ),
        )
        bindContext(context)
        persistNow()
        true
    }.getOrDefault(false)

    fun sizeText(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${numberFormat.format(bytes / 1024f)} KB"
        else -> "${numberFormat.format(bytes / 1024f / 1024f)} MB"
    }

    fun progressOf(task: DownloadTask): Float {
        if (task.state == DownloadState.DONE) return 1f
        if (task.total <= 0) return 0f
        return (task.bytes.toFloat() / task.total).coerceIn(0f, 1f)
    }

    private const val MAX_ATTEMPTS = 3

    /** 任务级自动重试上限轮次与退避阶梯（毫秒） */
    private const val MAX_AUTO_ROUNDS = 3
    private val RETRY_BACKOFF_MS = longArrayOf(2_000, 5_000, 15_000)

    /** 分段分裂的最小剩余量：剩余不足这个数的分段不再劈开 */
    private const val SPLIT_MIN_REMAINING = 512L * 1024
    /** 分段下载线程数（设置里可选 4/8/16/32/64） */
    @Volatile
    var segments = 4

    /** 文件超过该字节数才使用多线程分段下载 */
    @Volatile
    var segmentMinBytes = 1024L * 1024
}
