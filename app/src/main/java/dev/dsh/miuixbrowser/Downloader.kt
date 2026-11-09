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

enum class DownloadState { RUNNING, PAUSED, DONE, FAILED }

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
        DownloadService.start(context)
        DownloadService.showStart(context, fileName, task.id)
        launch(context, task.id)
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

    /** 外部链接分诊：仅凭 URL 形态判断是否直链下载（不发网络请求）。路径扩展名命中或已知下载主机即算。 */
    fun looksLikeFileUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (host in DOWNLOAD_HOSTS) return true
        val segment = uri.path?.substringAfterLast('/') ?: return false
        val ext = segment.substringAfterLast('.', "").lowercase()
        return ext in FILE_EXTENSIONS
    }

    /** 探测结果：[size] < 0 表示未知；[fileName] 是 Content-Disposition 推导名（可空）。 */
    data class ProbeResult(val size: Long, val fileName: String?, val mime: String?)

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
                head.disconnect()
                if (size < 0 || headCode >= 400) {
                    val ranged = openProbe(url, userAgent, cookie, "GET", range = true)
                    ranged.responseCode
                    size = ranged.getHeaderField("Content-Range")
                        ?.substringAfterLast('/')?.toLongOrNull()
                        ?: ranged.contentLengthLong
                    if (disposition == null) disposition = ranged.getHeaderField("Content-Disposition")
                    if (mime == null) mime = ranged.contentType?.substringBefore(';')
                    ranged.disconnect()
                }
                ProbeResult(
                    size = size,
                    fileName = disposition?.let { fileNameFrom(url, it) },
                    mime = mime,
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
    }

    fun resume(context: Context, id: Long) {
        bindContext(context)
        update(id) { it.copy(state = DownloadState.RUNNING, error = null) }
        persistNow()
        // 服务可能已随其它任务结束而停掉（或进程重启）：继续下载前先把服务与任务通知拉起来
        DownloadService.start(context)
        tasks.firstOrNull { it.id == id }?.let { DownloadService.showStart(context, it.name, id) }
        launch(context, id)
    }

    fun retry(context: Context, id: Long) {
        bindContext(context)
        update(id) { it.copy(state = DownloadState.RUNNING, error = null) }
        persistNow()
        DownloadService.start(context)
        tasks.firstOrNull { it.id == id }?.let { DownloadService.showStart(context, it.name, id) }
        launch(context, id)
    }

    /** 只有用户主动删除任务才清临时文件与分段进度；暂停 / 失败一律保留以便续传 */
    fun delete(context: Context, id: Long) {
        jobs.remove(id)?.cancel()
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

    private fun launch(context: Context, id: Long) {
        bindContext(context)
        jobs.remove(id)?.cancel()
        jobs[id] = scope.launch {
            var attempt = 0
            while (attempt < MAX_ATTEMPTS) {
                try {
                    downloadOnce(context, id)
                    val finished = tasks.firstOrNull { it.id == id }
                    segTables.remove(id)
                    update(id) { it.copy(state = DownloadState.DONE) }
                    persistNow()
                    DownloadService.finish(context, finished?.name ?: "文件", id)
                    toast(context, "下载完成：${finished?.name ?: "文件"}")
                    return@launch
                } catch (e: CancellationException) {
                    // 暂停 / 删除导致取消：分段进度已在各段写盘节流保存，这里再补一次确保不丢
                    persistNow()
                    return@launch
                } catch (e: Exception) {
                    attempt++
                    val message = e.message ?: e.javaClass.simpleName
                    if (attempt >= MAX_ATTEMPTS) {
                        update(id) { it.copy(state = DownloadState.FAILED, error = message) }
                        persistNow()
                        DownloadService.fail(context, "下载失败：$message", id)
                        return@launch
                    }
                    update(id) { it.copy(error = message) }
                    persistNow()
                    delay(1200L * attempt)
                }
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
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
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
                if (retryCode !in 200..299) throw IllegalStateException("HTTP $retryCode")
            }

            connection.inputStream.use { input ->
                java.io.FileOutputStream(file, append).use { out ->
                    val buffer = ByteArray(256 * 1024)
                    var written = existing
                    var lastUpdate = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 250) {
                            lastUpdate = now
                            update(id) { it.copy(bytes = written) }
                            val total = tasks.firstOrNull { it.id == id }?.total ?: 0L
                            val progress = if (total > 0) written.toFloat() / total else 0f
                            DownloadService.update(
                                context,
                                task.name,
                                progress,
                                "${(progress * 100).toInt()}% · ${sizeText(written)}",
                                id,
                            )
                        }
                    }
                    out.flush()
                    out.fd.sync()
                    update(id) { it.copy(bytes = written) }
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
        val threads = segments.coerceAtMost(((total / (256 * 1024)) + 1).toInt()).coerceAtLeast(1)
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
                update(id) { it.copy(bytes = written, total = total, state = DownloadState.RUNNING) }
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
                    "${(written * 100 / total)}% · ${sizeText(written)}",
                    id,
                    segPoints,
                    segMarks,
                )
            }
            coroutineScope {
                table.segments.forEach { seg ->
                    launch(Dispatchers.IO) {
                        val segSize = seg.end - seg.start + 1
                        var attempt = 0
                        while (seg.done < segSize) {
                            try {
                                downloadSegmentRange(task, raf, seg) { reportProgress() }
                                break
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                attempt++
                                if (attempt >= MAX_ATTEMPTS) throw e
                                delay(1200L * attempt)
                            }
                        }
                    }
                }
            }
            update(id) { it.copy(bytes = total, total = total) }
            persistNow()
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
            if (code != 206) throw IllegalStateException("HTTP $code")
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
    /** 分段下载线程数（设置里可选 4/8/16/32/64） */
    @Volatile
    var segments = 4

    /** 文件超过该字节数才使用多线程分段下载 */
    @Volatile
    var segmentMinBytes = 1024L * 1024
}
