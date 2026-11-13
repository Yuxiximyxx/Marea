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
    private var mediaDir: File? = null
    private val lock = Any()
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fullTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            runCatching {
                val dir = context.externalMediaDirs.firstOrNull() ?: context.filesDir
                dir.mkdirs()
                mediaDir = dir
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

    /** 导出诊断日志用：按与 init 相同的目录口径解析 diag.txt 路径。 */
    fun fileFor(context: Context): File {
        val dir = context.externalMediaDirs.firstOrNull() ?: context.filesDir
        return File(dir, "diag.txt")
    }

    /**
     * 设置页「导出诊断日志」：把最近崩溃堆栈（CrashCapture 的 last_crash.txt、
     * 旧版写外部目录的 crash.txt）与 Diag 日志尾部打包成一个文本文件
     * （cacheDir），供系统分享发回。失败返回 null。
     */
    fun buildExportFile(context: Context): File? = runCatching {
        val diagText = fileFor(context).takeIf { it.exists() }?.let { f ->
            val bytes = f.readBytes()
            val limit = 256 * 1024
            if (bytes.size > limit) {
                String(bytes, bytes.size - limit, limit, Charsets.UTF_8)
            } else {
                String(bytes, Charsets.UTF_8)
            }
        }
        val legacyCrash = runCatching {
            val dir = context.externalMediaDirs.firstOrNull()
                ?: context.getExternalFilesDir(null) ?: context.filesDir
            File(dir, "crash.txt").takeIf { it.exists() }?.readText()
        }.getOrNull()
        val sb = StringBuilder()
        sb.append("Marea 诊断导出\n")
        sb.append("导出时间：").append(fullTime.format(Date())).append('\n')
        sb.append("\n===== last_crash.txt（最近一次崩溃堆栈） =====\n")
        sb.append(CrashCapture.read(context) ?: "（无记录）").append('\n')
        sb.append("\n===== crash.txt（旧版崩溃记录） =====\n")
        sb.append(legacyCrash ?: "（无记录）").append('\n')
        sb.append("\n===== diag.txt（Diag 运行日志，尾部至多 256KB） =====\n")
        sb.append(diagText ?: "（无记录）").append('\n')
        // exp.10：glasskit 侧取证日志（材质档位到达/分支、底栏生效参数），文件在 filesDir。
        val glassText = runCatching {
            File(context.filesDir, dev.dsh.glasskit.GlassDiag.FILE_NAME)
                .takeIf { it.exists() }?.readText()
        }.getOrNull()
        sb.append("\n===== marea_glass_diag.log（玻璃/底栏取证日志） =====\n")
        sb.append(glassText ?: "（无记录）").append('\n')
        val name = "marea-diag-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        File(context.cacheDir, name).apply { writeText(sb.toString()) }
    }.getOrNull()

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
     * fork.8 用正则摘关键段（signal / backtrace 等）在用户机型上两次都「读到了流但没摘到段」——
     * 该机型墓碑格式与摘录模式对不上。fork.9 改为**原文转存**：把
     * [ApplicationExitInfo.getTraceInputStream] 的内容不做任何解析、截断到约 2MB 后
     * 整段写到同目录的 tombstone.txt（每次启动覆盖，只保留最近一次），diag 里只留
     * 一行指针。万一隐私页原生崩溃再现，原文直接可读，不再依赖格式猜测。
     * 流为空 / 读失败同样留一行原因，不静默。
     * exp.18：上限由 64KB 提到 2MB——exp.17 隐私页栈溢出崩溃时，64KB 恰好把崩溃
     * 线程的递归帧截断在外、拿不到确切函数名；2MB 足以容纳完整墓碑含全部线程回溯。
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
            val dir = mediaDir ?: file?.parentFile
            if (dir == null) {
                writeRaw("tombstone: latest CRASH_NATIVE at $stamp — media dir unknown, skip raw dump")
                return
            }
            val out = File(dir, "tombstone.txt")
            var total = 0
            stream.use { input ->
                FileOutputStream(out).use { fos ->
                    val buf = ByteArray(8192)
                    val limit = 2 * 1024 * 1024
                    while (total < limit) {
                        val n = input.read(buf, 0, minOf(buf.size, limit - total))
                        if (n < 0) break
                        fos.write(buf, 0, n)
                        total += n
                    }
                    fos.flush()
                    runCatching { fos.fd.sync() }
                }
            }
            writeRaw(
                "tombstone: latest CRASH_NATIVE at $stamp — 原始墓碑已整段转存 " +
                    "tombstone.txt（$total 字节，上限 2MB）: ${out.absolutePath}",
            )
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
