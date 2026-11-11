package dev.dsh.miuixbrowser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * 更新链路专用调度器（fork.10）：
 *
 * fork.8 的崩溃墓碑实证，自动更新拉取（GitHubAccelerate 直连+镜像竞速）在共享线程池的
 * 后台线程里做 TLS 握手时原生崩溃（conscrypt SEGV_ACCERR）、整进程陪葬。改版后更新检查
 * 只在用户手动点击时查、只直连自仓，也不再与全 App 共享线程池：拉取统一走这台单线程
 * 执行器，线程带 2MB 栈，给 TLS/JSON 解析留足余量；拉取代码本身全程 runCatching 兜底，
 * 失败只回 null 提示用户，不存在把异常或原生问题扩散到主进程其他部分的路径。
 */
val UpdateFetchDispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor(
    object : ThreadFactory {
        override fun newThread(r: Runnable): Thread =
            Thread(null, r, "marea-update-fetch", 2L * 1024 * 1024).apply { isDaemon = true }
    },
).asCoroutineDispatcher()

object UpdateChecker {
    /**
     * 下载新版本安装包：fork.10 起只用 release 自带的 GitHub 官方直链，不再挑线路。
     * （旧实现会先开一条裸线程跑 GitHubAccelerate.pickDownloadUrl 探测镜像，已删除。）
     * 上游 2.0.0 的 findPending / checkedThisProcess（启动自动检查）不合入：
     * fork 口径为自动更新已删除、只保留关于页手动检查（见文件尾注）。
     */
    fun download(context: Context, rel: AppRelease, openInApp: ((String) -> Unit)? = null) {
        val apk = rel.apkUrl
        if (apk.isNullOrBlank()) {
            // 无 APK：优先在应用内浏览页打开发布页，回退系统浏览器
            val page = rel.pageUrl
            if (openInApp != null && page.isNotBlank()) {
                openInApp(page)
            } else {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            return
        }
        Downloader.start(
            context = context,
            url = apk,
            userAgent = null,
            fileName = "Marea-${rel.version.removePrefix("v")}.apk",
            mimeType = "application/vnd.android.package-archive",
        )
        Toast.makeText(context, "已开始下载，完成后可在下载页安装", Toast.LENGTH_LONG).show()
    }
}

/** 是否有可更新的版本（启动自动检查与「检查更新」按钮共用）：按版本号逐段比较 */
fun isUpdateAvailable(rel: AppRelease): Boolean = isNewerVersion(rel.version, BuildConfig.VERSION_NAME)

/** 「已是最新版本」提示里显示的当前版本 */
fun currentVersionLabel(): String = BuildConfig.VERSION_NAME.removePrefix("v")

/**
 * 版本比较：按数字段逐段比较（v1.3.10 > v1.3.9）。
 * 数字完全相同时，本地若是 dev/alpha/beta/rc 预览版而远端是正式版，视为远端更新。
 */
fun isNewerVersion(remote: String, local: String): Boolean {
    fun nums(v: String) = Regex("\\d+").findAll(v).map { it.value.toLongOrNull() ?: 0L }.toList()
    val r = nums(remote)
    val l = nums(local)
    for (i in 0 until maxOf(r.size, l.size)) {
        val a = r.getOrElse(i) { 0L }
        val b = l.getOrElse(i) { 0L }
        if (a != b) return a > b
    }
    fun pre(v: String) = Regex("(dev|alpha|beta|rc|snapshot)", RegexOption.IGNORE_CASE).containsMatchIn(v)
    return pre(local) && !pre(remote)
}

// fork.10：自动检查的「发现新版本」底部弹窗（UpdateSheet）已删除——不再有自动检查，
// 新版本提示改走关于页手动检查后的弹窗（AboutScreen 内的 pendingUpdate 流）。
// 上游 2.0.0 曾恢复 UpdateSheet 与启动自动检查，合并时按 fork 口径继续不合入。
