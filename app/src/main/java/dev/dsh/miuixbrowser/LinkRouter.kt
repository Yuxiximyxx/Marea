package dev.dsh.miuixbrowser

import android.content.Context

/**
 * 外部 VIEW 链接分诊（fork.5；fork.6 改为「只判定、不自启」）。
 *
 * fork.5 的做法是分诊命中即在 MainActivity.onCreate / onNewIntent 里当场 startActivity 拉弹窗，
 * 紧接着 finish() / moveTaskToBack——冷启动下弹窗启动被系统吞掉、链接也随之丢失，
 * 外部表现是「点了没反应」。fork.6 起本类只返回判定结果，弹窗的实际启动由 MainActivity
 * 推迟到 onResume（Activity 真正到前台的时机）执行，启动失败再由它回退进浏览器打开，
 * 保证分诊的每个出口都有可见结果。
 *
 * 判定顺序与站内引擎语义一致：
 * 1. 分发规则优先——CLICK / DOWNLOAD 命中且非确认型，送出成功即算处理完（浏览器不打开）；
 *    确认型规则不在这里拦，照常进浏览器走既有确认链。
 * 2. DOWNLOAD 规则命中但送出失败（如没设目标应用）：带着改写后的地址打开新建下载弹窗。
 * 3. 形态上是直链下载（扩展名 / 已知下载主机）：打开悬浮的新建下载弹窗，不进浏览器、不建标签页。
 * 4. 其余普通链接：未处理，照常进浏览器。
 */
object LinkRouter {

    sealed interface Result {
        /** 已按规则送到目标应用（目标 App 已被 dispatch 拉起） */
        data object Dispatched : Result

        /** 应打开新建下载弹窗（启动时机与失败回退由 MainActivity 负责） */
        data class Dialog(val url: String) : Result

        /** 普通链接：照常进浏览器 */
        data object Unhandled : Result
    }

    fun routeExternal(context: Context, url: String): Result {
        for (scope in listOf(DispatchScope.CLICK, DispatchScope.DOWNLOAD)) {
            val (rule, finalUrl) = LinkDispatch.match(url, scope) ?: continue
            if (rule.confirm) continue
            if (LinkDispatch.dispatch(context, rule, finalUrl)) {
                Diag.log("LinkRouter: rule '${rule.name}' dispatched ($scope) url=${url.take(80)}")
                return Result.Dispatched
            }
            if (scope == DispatchScope.DOWNLOAD) {
                Diag.log("LinkRouter: DOWNLOAD rule '${rule.name}' dispatch failed -> dialog")
                return Result.Dialog(finalUrl)
            }
        }
        if (Downloader.looksLikeFileUrl(url)) {
            Diag.log("LinkRouter: file-like url -> dialog url=${url.take(80)}")
            return Result.Dialog(url)
        }
        Diag.log("LinkRouter: unhandled url=${url.take(80)}")
        return Result.Unhandled
    }
}
