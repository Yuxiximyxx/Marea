package dev.dsh.miuixbrowser

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 极简运行日志：写到 /sdcard/Android/media/<pkg>/diag.txt，方便闪退后看最后一步。
 *
 * fork.7 取证改造（此前两轮盲修都败在「日志没留下证据」上）：
 * - **追加模式，不再截断**：fork.6 及以前每次冷启动都 `writeText("")` 把文件清空，
 *   上一次会话的日志（包括在外部 App 点下载那次、点隐私入口闪退那次）全被下一次
 *   启动覆盖销毁——用户回传的 diag 只有最后一次冷启动的 5 行就是这么来的。
 *   现在跨会话保留；仅当文件超过 1MB 才在启动时删掉重开，防无限膨胀。
 * - **每行同步落盘**：write 后 flush + fd.sync()，进程猝死也尽量不丢最后一步。
 * - **退出原因回溯**：进程启动时用 ApplicationExitInfo 把系统记录的上一次退出
 *   （reason / description / 时间）写进来，区分原生崩溃 / ANR / 被杀 / 正常退出。
 */
object Diag {

    private var file: File? = null
    private val lock = Any()
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fullTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            runCatching {
                val dir = context.externalMediaDirs.firstOrNull() ?: context.filesDir
                dir.mkdirs()
                val f = File(dir, "diag.txt")
                if (f.exists() && f.length() > 1_000_000L) f.delete()
                file = f
                val version = runCatching {
                    val pi = context.packageManager.getPackageInfo(context.packageName, 0)
                    "${pi.versionName} (${pi.longVersionCode})"
                }.getOrDefault("unknown")
                writeRaw(
                    "==== session start ${fullTime.format(Date())} " +
                        "version=$version pid=${android.os.Process.myPid()} ====",
                )
                writeRaw("diag at ${f.absolutePath}")
                logExitHistory(context)
            }
        }
    }

    fun log(message: String) {
        runCatching { writeRaw("${time.format(Date())}  $message") }
    }

    /** 追加一行并同步落盘；未 init（file 为 null）时静默丢弃，与旧行为一致。 */
    private fun writeRaw(line: String) {
        synchronized(lock) {
            val f = file ?: return
            FileOutputStream(f, true).use { out ->
                out.write((line + "\n").toByteArray(Charsets.UTF_8))
                out.flush()
                runCatching { out.fd.sync() }
            }
        }
    }

    /** 把系统记录的历史进程退出原因写进 diag（最近 5 条，新到旧）。 */
    private fun logExitHistory(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            val reasons = am.getHistoricalProcessExitReasons(context.packageName, 0, 5)
            if (reasons.isEmpty()) {
                writeRaw("exit-history: (none recorded)")
                return
            }
            reasons.forEach { info ->
                writeRaw(
                    "exit-history: ${fullTime.format(Date(info.timestamp))} " +
                        "reason=${info.reason}(${exitReasonName(info.reason)}) status=${info.status} " +
                        "importance=${info.importance} desc=${info.description} " +
                        "pss=${info.pss}KB rss=${info.rss}KB",
                )
            }
            dumpLatestNativeTombstone(reasons)
        }
    }

    /**
     * fork.8：把系统记录的最近一次原生崩溃（CRASH_NATIVE）的墓碑关键段转存进 diag。
     *
     * JVM 崩溃有 crash.txt 兜底，但 SIGSEGV 这类原生崩溃只有系统留的 tombstone——
     * fork.7 已实证隐私页闪退是 CRASH_NATIVE + status 11(SIGSEGV)、且没有 crash.txt，
     * 定点定位靠的正是这份墓碑里的 signal / fault addr / backtrace。
     * [ApplicationExitInfo.getTraceInputStream] 是取它的官方入口。
     *
     * 只摘关键段并限制总量（最多扫 200K 字符、最多转存 48 行、backtrace 最多 24 帧），
     * 避免把 diag 撑爆或拖住启动；流为空/读失败也留一行原因，不静默。
     */
    private fun dumpLatestNativeTombstone(reasons: List<ApplicationExitInfo>) {
        val info = reasons.firstOrNull {
            it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE
        } ?: return
        val stamp = fullTime.format(Date(info.timestamp))
        runCatching {
            val stream = info.traceInputStream
            if (stream == null) {
                writeRaw("tombstone: latest CRASH_NATIVE at $stamp — trace stream is null（系统未留墓碑）")
                return
            }
            val picked = ArrayList<String>()
            var inBacktrace = false
            var frames = 0
            var budget = 200_000
            stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (budget <= 0 || picked.size >= 48) break
                    budget -= line.length + 1
                    val t = line.trim()
                    when {
                        t.startsWith("Build fingerprint:") -> picked += line
                        t.startsWith("pid:") -> picked += line
                        t.startsWith("signal ") -> picked += line
                        t.startsWith("Abort message:") -> picked += line
                        t == "backtrace:" -> {
                            inBacktrace = true
                            picked += line
                        }
                        inBacktrace && t.startsWith("#") -> {
                            if (frames < 24) {
                                picked += line
                                frames++
                            }
                        }
                        inBacktrace && t.isNotEmpty() -> inBacktrace = false
                    }
                }
            }
            if (picked.isEmpty()) {
                writeRaw("tombstone: latest CRASH_NATIVE at $stamp — 墓碑读到了但没摘到关键段")
            } else {
                writeRaw("tombstone: latest CRASH_NATIVE at $stamp — 关键段转存（backtrace 前 $frames 帧）：")
                picked.forEach { writeRaw("tombstone: $it") }
            }
        }.onFailure { e ->
            writeRaw(
                "tombstone: read failed for CRASH_NATIVE at $stamp: " +
                    "${e.javaClass.simpleName}: ${e.message}",
            )
        }
    }

    private fun exitReasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INIT_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
        else -> "UNKNOWN"
    }
}
