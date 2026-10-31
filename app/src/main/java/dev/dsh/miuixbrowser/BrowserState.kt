package dev.dsh.miuixbrowser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URLEncoder

/** 判断输入是网址还是搜索词。 */
fun looksLikeUrl(input: String): Boolean {
    val s = input.trim()
    if (s.isEmpty() || s.contains(" ")) return false
    if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("file://") || s.startsWith("about:")) return true
    if (s == "localhost" || s.startsWith("localhost:")) return true
    return Regex("^[A-Za-z0-9\\-._~%]+(\\.[A-Za-z0-9\\-._~%]+)+(/.*)?$").matches(s)
}

/** 把地址栏输入变成可加载的 URL，不像网址就走搜索。 */
fun toUrl(input: String, searchTemplate: String = "https://www.baidu.com/s?wd=%s"): String {
    val s = input.trim()
    return if (looksLikeUrl(s)) {
        if (s.startsWith("http") || s.startsWith("file") || s.startsWith("about")) s else "https://$s"
    } else {
        searchTemplate.replace("%s", URLEncoder.encode(s, "UTF-8"))
    }
}

fun hostOf(url: String): String = runCatching { Uri.parse(url).host ?: url }.getOrDefault(url)

/** 油猴 GM_* API 垫片（注入在所有用户脚本之前） */
private const val GM_SHIM = """
(function(){
  if (window.__mafariGM) return;
  window.unsafeWindow = window;
  window.GM_setValue = function(k, v){ try { localStorage.setItem('gm_' + k, JSON.stringify(v)); } catch(e){} };
  window.GM_getValue = function(k, d){ try { var v = localStorage.getItem('gm_' + k); return v === null ? d : JSON.parse(v); } catch(e){ return d; } };
  window.GM_deleteValue = function(k){ try { localStorage.removeItem('gm_' + k); } catch(e){} };
  window.GM_listValues = function(){ return Object.keys(localStorage).filter(function(k){return k.indexOf('gm_')===0;}).map(function(k){return k.slice(3);}); };
  window.GM_addStyle = function(css){ var s = document.createElement('style'); s.textContent = css; (document.head || document.documentElement).appendChild(s); return s; };
  window.GM_xmlhttpRequest = function(details){
    try {
      fetch(details.url, { method: details.method || 'GET', headers: details.headers || {}, body: details.data })
        .then(function(r){ return r.text().then(function(t){ return { r: r, t: t }; }); })
        .then(function(x){
          var resp = { status: x.r.status, statusText: x.r.statusText, responseText: x.t, finalUrl: x.r.url, readyState: 4 };
          details.onload && details.onload(resp);
          details.onreadystatechange && details.onreadystatechange(resp);
        })
        .catch(function(e){ details.onerror && details.onerror(e); });
    } catch(e) { details.onerror && details.onerror(e); }
  };
  window.GM_notification = function(o){ try { var t = typeof o === 'string' ? o : (o.text || o.title || ''); if (window.Notification) new Notification(t); } catch(e){} };
  window.GM_openInTab = function(u){ window.open(u, '_blank'); };
  window.GM_registerMenuCommand = function(){};
  window.GM_info = { scriptHandler: 'mafari', version: '2.5.0', script: { name: 'mafari userscript' } };
  window.__mafariGM = true;
})();
"""

/** 一个标签页：持有自己的 WebView 和页面状态。 */
@SuppressLint("SetJavaScriptEnabled")
class BrowserTab(private val appContext: Context) {

    /** 每个标签的唯一 id，Compose 用它做 key。 */
    val id: Int = nextId()

    var title by mutableStateOf("新标签页")
    var url by mutableStateOf("")
    var progress by mutableFloatStateOf(0f)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var errorText by mutableStateOf<String?>(null)
    /** 是否是从主页打开的第一个页面（用于「返回到主页」） */
    var fromHome by mutableStateOf(false)
        private set

    var closed = false
    private var destroyed = false

    /** 当前标签是否「请求桌面网站」 */
    var desktopMode by mutableStateOf(false)
        private set

    private val defaultUserAgent: String by lazy { webView.settings.userAgentString }

    /** 设置里的自定义 UA（空串 = 跟随系统） */
    var customUserAgent: String = ""

    /** 长按链接时由界面挂上回调。 */
    var onLongPressLink: ((String) -> Unit)? = null

    /** 用户脚本扩展（页面加载完后注入） */
    var userScripts: List<UserScript> = emptyList()

    /** 搜索模板，由设置注入 */
    var searchTemplate: String = "https://www.baidu.com/s?wd=%s"

    /** 下拉进度 0..1，用于顶部动画 */
    var pullProgress by mutableFloatStateOf(0f)
        private set

    /** 下载请求回调（交给界面弹确认框） */
    var onDownload: ((url: String, userAgent: String?, disposition: String?, mime: String?) -> Unit)? = null

    /** 下拉刷新回调 */
    var onPullRefresh: (() -> Unit)? = null

    /** 遇到 .user.js 链接 */
    var onUserScriptLink: ((String) -> Unit)? = null

    /** 记录历史 */
    var onVisited: ((url: String, title: String) -> Unit)? = null

    val webView: WebView = WebView(appContext)

    init {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString.replace("; wv", "")
        }
        // 打开 WebView 的 WebAuthn（Passkey）支持
        runCatching {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.WEB_AUTHENTICATION)) {
                androidx.webkit.WebSettingsCompat.setWebAuthenticationSupport(
                    webView.settings,
                    androidx.webkit.WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_NONE,
                )
            }
        }
        webView.setBackgroundColor(0xFFFFFFFF.toInt())
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, pageUrl: String?, favicon: Bitmap?) {
                pageUrl?.let { url = it }
                loading = true
                errorText = null
                syncNav()
            }

            override fun onPageFinished(view: WebView, pageUrl: String?) {
                pageUrl?.let { url = it }
                loading = false
                progress = 1f
                syncNav()
                onVisited?.invoke(url, title)

                if (userScripts.any { it.enabled && it.matches(url) }) {
                    runCatching { view.evaluateJavascript(GM_SHIM, null) }
                }
                userScripts.filter { it.enabled && it.matches(url) }.forEach { script ->
                    runCatching { view.evaluateJavascript(script.code, null) }
                }
            }

            override fun doUpdateVisitedHistory(view: WebView, pageUrl: String?, isReload: Boolean) {
                pageUrl?.let { url = it }
                syncNav()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) errorText = error.description?.toString()
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url.toString()
                if (target.endsWith(".user.js") || target.contains("greasyfork") && target.contains(".user.js")) {
                    onUserScriptLink?.invoke(target)
                    return true
                }
                val scheme = request.url.scheme ?: return false
                return when (scheme) {
                    "http", "https", "file", "about", "data", "javascript" -> false
                    else -> {
                        // 支付宝 / 微信 / 淘宝等外部协议交给系统处理
                        runCatching {
                            appContext.startActivity(
                                Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        true
                    }
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress = newProgress / 100f
                loading = newProgress < 100
            }

            override fun onReceivedTitle(view: WebView, pageTitle: String?) {
                if (!pageTitle.isNullOrBlank()) title = pageTitle
            }
        }

        webView.setDownloadListener { link, userAgent, contentDisposition, mimeType, _ ->
            onDownload?.invoke(link, userAgent, contentDisposition, mimeType)
        }

        // 约 2.5cm 的拉动距离，避免误触
        val pullThresholdPx = webView.context.resources.displayMetrics.xdpi / 2.54f * 4f
        var touchDownY = 0f
        var pulled = 0f
        webView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    touchDownY = event.y
                    pulled = 0f
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (url.isNotEmpty() && webView.scrollY <= 0 && event.y > touchDownY) {
                        pulled = event.y - touchDownY
                        pullProgress = (pulled / pullThresholdPx).coerceIn(0f, 1f)
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (url.isNotEmpty() && pulled > pullThresholdPx && webView.scrollY <= 0) {
                        onPullRefresh?.invoke()
                    }
                    pulled = 0f
                    pullProgress = 0f
                }
            }
            false
        }

        webView.setOnLongClickListener {
            val result = webView.hitTestResult
            val isLink = result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            val link = result.extra
            if (isLink && !link.isNullOrEmpty()) {
                onLongPressLink?.invoke(link)
                true
            } else {
                false
            }
        }
    }

    /** 注入用户脚本：start 阶段只注入 @run-at document-start 的，finish 阶段注入其余 */
    private fun injectUserScripts(view: WebView, documentEnd: Boolean) {
        val matched = userScripts.filter { it.enabled && it.matches(url) }
        if (matched.isEmpty()) return
        runCatching { view.evaluateJavascript(GM_SHIM, null) }
        matched.forEach { script ->
            val isStart = script.runAt == "document-start"
            if (isStart == !documentEnd) {
                val safe = script.code.replace("</script", "<\\/script")
                runCatching {
                    view.evaluateJavascript(
                        "try{(function(){var GM_info={scriptHandler:'mafari',version:'2.6.0'};$safe})();}catch(e){console.error(e);}",
                        null,
                    )
                }
            }
        }
    }

    private fun syncNav() {
        canGoBack = runCatching { webView.canGoBack() }.getOrDefault(false) || fromHome
        canGoForward = runCatching { webView.canGoForward() }.getOrDefault(false)
    }

    fun load(input: String) {
        val target = toUrl(input, searchTemplate)
        if (url.isEmpty()) fromHome = true
        url = target
        webView.loadUrl(target)
        syncNav()
    }

    fun loadUrl(raw: String) {
        url = raw
        webView.loadUrl(raw)
    }

    fun reload() {
        if (loading) {
            webView.stopLoading()
            loading = false
        } else {
            webView.reload()
        }
    }

    /** 应用 UA：桌面模式优先，其次设置里的自定义 UA，最后系统默认 */
    fun applyUserAgent() {
        val ua = when {
            desktopMode -> DESKTOP_UA
            customUserAgent.isBlank() || customUserAgent == DESKTOP_UA -> defaultUserAgent
            else -> customUserAgent
        }
        runCatching { webView.settings.userAgentString = ua }
    }

    fun toggleDesktopMode(enabled: Boolean) {
        desktopMode = enabled
        runCatching {
            webView.settings.useWideViewPort = true
            webView.settings.loadWithOverviewMode = true
        }
        applyUserAgent()
        val target = webView.url ?: url
        if (target.isNotBlank() && !target.startsWith("about:")) {
            webView.loadUrl(target)
        } else {
            webView.reload()
        }
    }

    /** 页内查找 */
    fun findAll(text: String) {
        if (text.isBlank()) webView.clearMatches() else webView.findAllAsync(text)
    }

    fun findNext(forward: Boolean) {
        runCatching { webView.findNext(forward) }
    }

    fun clearFind() {
        runCatching { webView.clearMatches() }
    }

    /** 回主页 */
    fun goHome() {
        fromHome = false
        url = ""
        errorText = null
        loading = false
        syncNav()
    }

    fun goBack() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else if (fromHome) {
            // 回到主页（主页是 Compose 覆盖层，把 url 清空即可），而不是退出
            fromHome = false
            url = ""
            errorText = null
            syncNav()
        }
    }

    fun goForward() {
        if (webView.canGoForward()) webView.goForward()
    }

    fun destroyNow() {
        if (destroyed) return
        destroyed = true
        runCatching {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
    }

    /** 关闭标签：等界面把 WebView 从视图树摘下来后再销毁。 */
    fun close() {
        closed = true
        webView.post { if (webView.parent == null) destroyNow() }
    }

    private companion object {
        private var counter = 0

        fun nextId(): Int {
            counter += 1
            return counter
        }
    }
}

/** 多标签状态。 */
class BrowserState(private val context: Context) {

    val tabs = mutableStateListOf<BrowserTab>()

    /** 全局 UA，新标签也用它 */
    var userAgent: String = ""

    var activeIndex by mutableIntStateOf(0)
        private set

    val activeTab: BrowserTab
        get() = tabs[activeIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))]

    init {
        newTab()
    }

    fun newTab(target: String = "") {
        val tab = BrowserTab(context)
        tab.customUserAgent = userAgent
        tab.applyUserAgent()
        tabs.add(tab)
        activeIndex = tabs.lastIndex
        if (target.isNotEmpty()) tab.load(target)
    }

    fun applyUserAgent(ua: String) {
        userAgent = ua
        tabs.forEach {
            it.customUserAgent = ua
            it.applyUserAgent()
        }
    }

    fun select(index: Int) {
        if (index in tabs.indices) activeIndex = index
    }

    fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val tab = tabs.removeAt(index)
        tab.close()
        if (tabs.isEmpty()) {
            newTab()
        } else {
            activeIndex = index.coerceAtMost(tabs.lastIndex)
        }
    }

    fun newTabInBackground(target: String) {
        val tab = BrowserTab(context)
        tabs.add(tab)
        tab.load(target)
    }

    fun closeAll() {
        tabs.forEach { it.close() }
        tabs.clear()
        newTab()
    }
}
