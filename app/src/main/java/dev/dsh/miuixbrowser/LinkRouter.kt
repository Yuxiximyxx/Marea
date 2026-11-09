package dev.dsh.miuixbrowser

import android.content.Context

/**
 * 外部 VIEW 链接判定（fork.5 引入；fork.7 改案后只剩分发规则这一件事）。
 *
 * 历史：fork.5 在这里按 URL 形态预判直链下载、由 MainActivity 转手拉起下载弹窗，
 * fork.6 又把启动推迟到 onResume——两轮真机都证明「主界面分诊转弹窗」这条链不可靠。
 * fork.7 按 ABDM 的结构改案：下载弹窗（DownloadDialogActivity）以 intent-filter
 * 被系统直接唤起，不再经主界面转手；本类回归单一职责——到达主界面的外部链接只做
 * 分发规则判定：命中且送出成功 = 已处理（浏览器不打开）；其余一律进浏览器
 * （含 DOWNLOAD 规则送出失败：落回浏览器，站内下载链会接管）。
 * 确认型规则不在这里拦，照常进浏览器走既有确认链。
 */
object LinkRouter {

    sealed interface Result {
        /** 已按规则送到目标应用（目标 App 已被 dispatch 拉起） */
        data object Dispatched : Result

        /** 未被规则送出：照常进浏览器 */
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
                // fork.7 改案：送出失败不再转弹窗（弹窗已是独立系统入口），落回浏览器打开原链接
                Diag.log("LinkRouter: DOWNLOAD rule '${rule.name}' dispatch failed -> browser")
            }
        }
        Diag.log("LinkRouter: unhandled url=${url.take(80)}")
        return Result.Unhandled
    }
}
