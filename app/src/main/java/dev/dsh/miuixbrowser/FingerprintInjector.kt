package dev.dsh.miuixbrowser

import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.net.IDN

/**
 * 一个 web origin：scheme://host[:port]，与 Chromium 的序列化形式一致
 * （host 小写 punycode，默认端口省略，IPv6 带方括号），可直接作为 addDocumentStartJavaScript 的 origin 规则，
 * 也可与页面里的 self.origin 逐字比较。
 */
class WebOrigin private constructor(
    val scheme: String,
    val host: String,
    val port: Int,
) {
    /** 规范化 origin，同时也是注入规则（不含通配符、不含路径）。 */
    val origin: String =
        if (port == -1) "$scheme://$host" else "$scheme://$host:$port"

    override fun equals(other: Any?) = other is WebOrigin && other.origin == origin
    override fun hashCode() = origin.hashCode()
    override fun toString() = origin

    companion object {
        /** 只接受 http / https；解析失败、无主机名、带通配符的一律返回 null。 */
        fun parse(url: String?): WebOrigin? {
            if (url.isNullOrBlank()) return null
            val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            var host = uri.host?.lowercase()?.trimEnd('.') ?: return null
            if (host.isEmpty() || '*' in host) return null
            host = if (':' in host) {
                if (host.startsWith("[")) host else "[$host]"
            } else {
                runCatching { IDN.toASCII(host, IDN.ALLOW_UNASSIGNED) }.getOrNull()?.lowercase() ?: return null
            }
            var port = uri.port
            if ((scheme == "http" && port == 80) || (scheme == "https" && port == 443)) port = -1
            if (port != -1 && port !in 1..65535) return null
            return WebOrigin(scheme, host, port)
        }
    }
}

/**
 * 指纹保护的注入器：每个 WebView 一个实例，只在主线程使用。
 *
 * 主架构：origin-scoped document-start 注入 + 内联配置。
 * - 对每个「允许注入」的 origin，原生侧生成一份只含该 origin 配置的脚本，用
 *   addDocumentStartJavaScript(view, script, setOf(origin)) 注册；来源匹配由 WebView 完成。
 * - 配置不放进任何页面可读的位置：没有 JS 桥、没有全局变量，所有 origin 的配置也从不合并进同一份脚本。
 * - 「允许注入的 origin」（本类里的 [handlers]）与「保护豁免列表」（[AdBlock.fpExemptHosts]）是两个概念：
 *   前者只表示「已经为它注册了脚本」，后者命中时一定不注册（并撤销已有注册）。
 *
 * 动态导航：注入脚本只作用于「注册完成之后才开始加载」的文档，所以必须在导航开始前调用 [prepare]：
 * Mafari 自己 loadUrl / reload / 前进后退、以及 shouldOverrideUrlLoading 里的主框架导航都会先注册再放行；
 * 服务端重定向的落地页、没有经过任何回调的导航只能尽力而为。不能宣称对所有未知来源都生效。
 *
 * 降级路径：[docStartSupported] 为 false 时，不注册脚本，改由 [FpFallbackBridge]（无参 fp()）提供
 * 原生维护的顶层 origin 的配置，只保护主框架 / 同源 iframe，安全边界弱于主路径。
 */
class FingerprintInjector(private val webView: WebView) {

    /** 必须用 isFeatureSupported 运行时判断，不能只看 Android / Chromium 版本。 */
    val docStartSupported: Boolean =
        runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }
            .getOrDefault(false)

    /** 已注册的 origin → 句柄；访问顺序 LRU，超出上限时撤销最久未用的。 */
    private val handlers = LinkedHashMap<String, ScriptHandler>(16, 0.75f, true)

    private var generation = AdBlock.fingerprintGeneration

    /** 原生维护的最近一次顶层导航地址，仅供降级路径的 [fallbackConfig] 使用。 */
    @Volatile
    private var topUrl: String = ""

    /**
     * 在导航开始之前调用（主线程）。url 为即将加载的主框架地址。
     * 保护关闭 / 命中豁免 / 不是 http(s) 时，确保该 origin 没有残留注册。
     */
    fun prepare(url: String?) {
        if (!url.isNullOrBlank() && !url.startsWith("about:", ignoreCase = true)) topUrl = url
        if (!docStartSupported) return
        val origin = WebOrigin.parse(url) ?: return
        syncGeneration()
        val key = origin.origin
        val config = AdBlock.fingerprintConfigFor(origin)
        if (config == null) {
            handlers.remove(key)?.let { runCatching { it.remove() } }
            return
        }
        if (handlers[key] != null) return // get() 同时刷新 LRU 顺序
        val script = AdBlock.fingerprintScriptFor(origin, config)
        if (script.isEmpty()) return
        val handler = runCatching {
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf(key))
        }.onFailure { Diag.log("fingerprint: register $key failed ${it.message}") }.getOrNull() ?: return
        handlers[key] = handler
        while (handlers.size > MAX_ORIGINS) {
            val eldest = handlers.entries.first()
            handlers.remove(eldest.key)
            runCatching { eldest.value.remove() }
        }
    }

    /**
     * 前进 / 后退不会经过 shouldOverrideUrlLoading，需要先为目标历史项注册。
     * offset：-1 后退，+1 前进。
     */
    fun prepareHistory(offset: Int) {
        runCatching {
            val list = webView.copyBackForwardList()
            val index = list.currentIndex + offset
            if (index in 0 until list.size) prepare(list.getItemAtIndex(index)?.url)
        }
    }

    /** onPageStarted：降级路径里补跑脚本；主路径里只做「漏网」补注册（对之后的加载生效）。 */
    fun onPageStarted(pageUrl: String?) {
        if (!pageUrl.isNullOrBlank() && !pageUrl.startsWith("about:", ignoreCase = true)) topUrl = pageUrl
        if (docStartSupported) {
            prepare(pageUrl)
            return
        }
        val origin = WebOrigin.parse(pageUrl) ?: return
        if (AdBlock.fingerprintConfigFor(origin) == null) return
        val js = AdBlock.fingerprintJs()
        if (js.isNotEmpty()) webView.evaluateJavascript(js, null)
    }

    /** 设置或豁免列表变化后，由 [prepare] 懒检测并撤销全部旧注册（旧脚本里内联着旧配置）。 */
    private fun syncGeneration() {
        val g = AdBlock.fingerprintGeneration
        if (g == generation) return
        generation = g
        clear()
    }

    fun clear() {
        handlers.values.forEach { runCatching { it.remove() } }
        handlers.clear()
    }

    /**
     * 降级路径专用，任意线程。返回 "掩码|种子|origin"，没有配置返回空串。
     * 只给出「当前顶层 origin」自己的配置——页面最多读到自己的配置，读不到别的站点的。
     */
    fun fallbackConfig(): String {
        if (docStartSupported) return ""
        val origin = WebOrigin.parse(topUrl) ?: return ""
        val config = AdBlock.fingerprintConfigFor(origin) ?: return ""
        return "${config.mask}|${config.seed}|${origin.origin}"
    }

    private companion object {
        /** 每个 WebView 同时保留的 origin 注册数；每份脚本约 25 KB，上限也限制了内存占用。 */
        const val MAX_ORIGINS = 16
    }
}

/**
 * 降级路径的无参桥（对象名 MafariFp），只在 WebView 不支持 DOCUMENT_START_SCRIPT 时安装。
 * 没有 host / topHost 参数：页面传什么都影响不了返回值。
 */
class FpFallbackBridge(private val injector: FingerprintInjector) {
    @JavascriptInterface
    fun fp(): String = injector.fallbackConfig()
}
