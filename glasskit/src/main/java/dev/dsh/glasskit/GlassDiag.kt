package dev.dsh.glasskit

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * exp.10 返工取证日志：档位到达/分支与底栏、导航条沉浸相关的运行时事实
 * 同时打 Logcat（标签 MareaGlass）并追加到应用私有文件 marea_glass_diag.log，
 * 该文件会被 Marea 设置页的「导出诊断日志」一并打包。
 */
object GlassDiag {

    const val FILE_NAME = "marea_glass_diag.log"
    private const val TAG = "MareaGlass"
    private const val MAX_FILE_BYTES = 512 * 1024L

    private val lock = Any()
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun log(context: Context?, source: String, message: String) {
        val line = "${timeFormat.format(Date())} [$source] $message"
        Log.i(TAG, line)
        if (context == null) return
        synchronized(lock) {
            runCatching {
                val file = File(context.applicationContext.filesDir, FILE_NAME)
                if (file.exists() && file.length() > MAX_FILE_BYTES) {
                    file.delete()
                }
                file.appendText(line + "\n")
            }
        }
    }
}
