package dev.dsh.miuixbrowser

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * exp.3 取证：全局未捕获异常落盘。
 *
 * exp.1/exp.2 两轮黑屏修复都没有真机堆栈、靠代码审计猜根因，exp.2 已被真机
 * 证伪。本类把崩溃堆栈同步写进应用私有目录 last_crash.txt，供设置页
 * 「导出诊断日志」连同 Diag 日志一起打包发回；写完仍把异常交回原默认处理
 * （Legado CrashHandler 的报告/结束流程），不改变崩溃本身的表现。
 *
 * 安装点与 Diag.init 相同的一组入口 Activity（Application 类是 Legado 的
 * final App、不可子类化）：进程的现实入口都会先过其中之一，install 幂等。
 */
object CrashCapture {

    private const val FILE_NAME = "last_crash.txt"

    @Volatile
    private var installed = false

    fun crashFile(context: Context): File = File(context.filesDir, FILE_NAME)

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                crashFile(appContext).writeText(
                    "==== Marea last crash $stamp ====\n" +
                        "thread: ${thread.name}\n" +
                        throwable.stackTraceToString(),
                )
                Diag.log(
                    "crash captured -> last_crash.txt: " +
                        "${throwable.javaClass.name}: ${throwable.message}",
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
        Diag.log("crash capture installed")
    }

    /** 最近一次崩溃堆栈全文；从未崩溃过返回 null。 */
    fun read(context: Context): String? = runCatching {
        crashFile(context).takeIf { it.exists() && it.length() > 0 }?.readText()
    }.getOrNull()
}
