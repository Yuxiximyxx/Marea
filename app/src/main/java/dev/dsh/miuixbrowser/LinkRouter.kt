package dev.dsh.miuixbrowser

import android.content.Context

/**
 * 外部 VIEW 链接分诊（fork.5）：MainActivity 收到外部链接后、交给浏览器主界面之前先过这里。
 *
 * 顺序与站内引擎语义一致：
 * 1. 分发规则优先——CLICK / DOWNLOAD 命中且非确认型，送出成功即算处理完（浏览器不打开）；
 *    确认型规则不在这里拦，照常进浏览器走既有确认链。
 * 2. DOWNLOAD 规则命中但送出失败（如没设目标应用）：带着改写后的地址打开新建下载弹窗。
 * 3. 形态上是直链下载（扩展名 / 已知下载主机）：打开悬浮的新建下载弹窗，不进浏览器、不建标签页。
 * 4. 其余普通链接：返回 false，照常进浏览器。
 */
object LinkRouter {

    fun tryHandleExternal(context: Context, url: String): Boolean {
        for (scope in listOf(DispatchScope.CLICK, DispatchScope.DOWNLOAD)) {
            val (rule, finalUrl) = LinkDispatch.match(url, scope) ?: continue
            if (rule.confirm) continue
            if (LinkDispatch.dispatch(context, rule, finalUrl)) return true
            if (scope == DispatchScope.DOWNLOAD) {
                openDownloadDialog(context, finalUrl)
                return true
            }
        }
        if (Downloader.looksLikeFileUrl(url)) {
            openDownloadDialog(context, url)
            return true
        }
        return false
    }

    private fun openDownloadDialog(context: Context, url: String) {
        context.startActivity(DownloadDialogActivity.intent(context, url))
    }
}
