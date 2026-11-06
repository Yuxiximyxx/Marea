package dev.dsh.miuixbrowser

import android.content.Context
import java.io.File

/**
 * 只清理「无用缓存」：下载器 / blob 抽取遗留在 cacheDir 里、已经没有任务引用的临时文件。
 * 不碰：WebView 网页缓存与 Cookie、图标缓存、设置、收藏、历史、脚本、已下载的文件，
 * 也不碰仍在进行 / 暂停中的下载任务对应的分片文件。
 */
object CacheCleaner {

    private val dlPattern = Regex("^mafari_dl_(\\d+)\\.(part|seg)$")
    private const val BLOB_PREFIX = "mafari_blob_"

    /** 冷启动清理的保护窗口：刚创建的文件可能属于刚发起的操作，留给下次 */
    private const val COLD_START_MIN_AGE_MS = 60_000L

    /** 手动清理的保护窗口：避免误删正在抽取的大 blob */
    private const val MANUAL_MIN_AGE_MS = 10 * 60_000L

    @Volatile
    private var coldStartDone = false

    /** 每个进程只执行一次（冷启动）；后台线程执行，不阻塞界面 */
    fun runOnColdStart(context: Context) {
        if (coldStartDone) return
        coldStartDone = true
        val app = context.applicationContext
        Thread {
            val freed = runCatching { clean(app, COLD_START_MIN_AGE_MS) }.getOrDefault(0L)
            Diag.log("cache cleanup on cold start freed=$freed bytes")
        }.start()
    }

    /** 手动清理 */
    fun cleanNow(context: Context): Long = clean(context.applicationContext, MANUAL_MIN_AGE_MS)

    /** 当前可清理的大小 */
    fun reclaimableBytes(context: Context, manual: Boolean = true): Long =
        candidates(context.applicationContext, if (manual) MANUAL_MIN_AGE_MS else COLD_START_MIN_AGE_MS)
            .sumOf { it.length() }

    private fun clean(context: Context, minAgeMs: Long): Long {
        var freed = 0L
        for (f in candidates(context, minAgeMs)) {
            val size = f.length()
            if (runCatching { f.delete() }.getOrDefault(false)) freed += size
        }
        return freed
    }

    private fun candidates(context: Context, minAgeMs: Long): List<File> {
        val files = context.cacheDir.listFiles() ?: return emptyList()
        val now = System.currentTimeMillis()
        // 仍在列表里的下载任务（进行中 / 暂停 / 失败可重试）对应的分片文件要保留
        val liveIds = runCatching { Downloader.tasks.toList().map { it.id } }.getOrDefault(emptyList()).toSet()
        return files.filter { f ->
            if (!f.isFile || now - f.lastModified() < minAgeMs) return@filter false
            when {
                f.name.startsWith(BLOB_PREFIX) && f.name.endsWith(".part") -> true
                else -> dlPattern.matchEntire(f.name)?.let { m -> m.groupValues[1].toLongOrNull() !in liveIds } ?: false
            }
        }
    }
}
