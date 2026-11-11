package dev.dsh.miuixbrowser

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * fork.25：外部链接的覆盖式入口（替代 MainActivity 直收系统 VIEW 意图）。
 *
 * fork.24 之后外部下载的逻辑已对（先分诊、自有面板先行），但呈现还剩一帧闪：
 * 意图落在不透明全屏的 MainActivity（主任务）上，系统先跑一遍「切到另一个应用」
 * 的任务切换动画——来源应用以圆角卡片形式滑走、底下是黑的，随后透明的下载弹窗
 * 才落定（2026-11-12 真机录屏逐帧实证）。对照组 LinkGo→ABDM 全程无此帧，因为
 * 那条链路上没有任何不透明全屏 Activity 被切到前台。
 *
 * 本 Activity 只做同步分诊、全程不绘制任何内容：
 * - 半透明主题 + windowDisablePreview（冷启动 starting window 同样透明）；
 * - 独立 taskAffinity + excludeFromRecents，与主任务分家；
 * - linkOpenMode=0 且命中下载形态（fork.24 的 looksLikeFileUrl，纯字符串判定、
 *   零网络）→ 同任务拉起 DownloadDialogActivity（透明主题），本 Activity 随即
 *   finish——整条链只有半透明窗口，来源应用钉在原地、面板直接浮出，无切换帧；
 * - 其余（普通网页、弹窗逃生口回流、自定义标签页 / 交给其他浏览器两种打开模式）
 *   把原意图原样（action/data/extras 不变）显式转交 MainActivity，走原 LinkRouter
 *   全流程——打开浏览器本就是正常全屏切换，不在此列。拉起弹窗失败时同样回落
 *   转交主界面打开，与 fork.24 旧口径一致。
 *
 * 系统侧的 VIEW http/https + BROWSABLE 过滤器随入口一并从 MainActivity 移到
 * 这里，包级解析不变（默认浏览器候选资格只看包内有无匹配过滤器）。
 */
class ExternalLinkActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diag.init(this)
        handleIntent(intent)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun handleIntent(source: Intent?) {
        val data = source?.takeIf { it.action == Intent.ACTION_VIEW }?.data
        val scheme = data?.scheme?.lowercase()
        Diag.log("ExternalLinkActivity: action=${source?.action} data=$data")
        if (source == null || data == null || (scheme != "http" && scheme != "https")) {
            forwardToMain(source)
            return
        }
        val url = data.toString()
        // 与 MainActivity.consumeViewIntent 的 fork.24 分诊严格同判：仅默认打开
        // 模式（linkOpenMode=0）走外部下载分诊；另两种模式原样转交主界面处理。
        // 弹窗逃生口回来的意图不重复拦截（防循环，与主界面分诊内同判）。
        val fromDialogEscape = source.getBooleanExtra(MainActivity.EXTRA_OPEN_IN_BROWSER, false)
        if (!fromDialogEscape &&
            AppSettings(this).linkOpenMode == 0 &&
            Downloader.looksLikeFileUrl(url)
        ) {
            val launched = runCatching {
                startActivity(DownloadDialogActivity.intent(this, url))
            }.isSuccess
            Diag.log("ExternalLinkActivity: file-like -> download dialog launched=$launched: ${url.take(100)}")
            if (launched) return
        }
        forwardToMain(source)
    }

    /** 原意图原样转交主界面：只换显式组件，action / data / extras 一概不动 */
    private fun forwardToMain(source: Intent?) {
        if (source == null) return
        val forward = Intent(source).apply {
            component = ComponentName(this@ExternalLinkActivity, MainActivity::class.java)
        }
        runCatching { startActivity(forward) }
    }
}
