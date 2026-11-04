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
import androidx.compose.ui.graphics.asImageBitmap
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

fun hostOf(url: String): String {
    val trimmed = url.trim()
    if (trimmed.startsWith("data:", ignoreCase = true)) return "内嵌图片"
    val host = runCatching { Uri.parse(trimmed).host }.getOrNull()
    return if (!host.isNullOrBlank()) host else trimmed.take(48)
}

/** 脚本菜单命令（GM_registerMenuCommand） */
data class ScriptMenuCommand(val id: String, val title: String, val script: String)

/** 链接是否指向 .user.js（忽略查询串与片段） */
fun isUserScriptUrl(url: String): Boolean {
    val path = runCatching { Uri.parse(url).path }.getOrNull().orEmpty()
    return path.endsWith(".user.js", ignoreCase = true)
}

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
    /** 标签卡片用的页面缩略图（页面加载完成后截取） */
    var thumbnail by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    /** 是否是从主页打开的第一个页面（用于「返回到主页」） */
    var fromHome by mutableStateOf(false)
        private set

    var closed = false
    private var destroyed = false

    /** 当前标签是否「请求桌面网站」 */
    var desktopMode by mutableStateOf(false)
        private set

    /** 无痕浏览标签 */
    var incognito by mutableStateOf(false)
        private set

    fun applyIncognito(value: Boolean) {
        if (incognito == value) return
        incognito = value
        // 无痕：不持久化 Cookie / 缓存写入偏好简化为关闭第三方 Cookie 接受
        val cm = CookieManager.getInstance()
        if (value) {
            cm.setAcceptCookie(false)
        } else {
            cm.setAcceptCookie(true)
        }
    }

    private val defaultUserAgent: String by lazy { webView.settings.userAgentString }

    /** 设置里的自定义 UA（空串 = 跟随系统） */
    var customUserAgent: String = ""

    /** 长按链接时由界面挂上回调。 */
    var onLongPressLink: ((String) -> Unit)? = null

    /** 长按图片 */
    var onLongPressImage: ((String) -> Unit)? = null

    /** 长按普通元素：由页面内 JS 判定为广告浮层后回调 */
    var onElementLongPress: ((ElementHit) -> Unit)? = null

    /** 用户脚本扩展：通过 document-start 注入，由运行时按各框架 URL 与 run-at 自行调度 */
    var userScripts: List<UserScript> = emptyList()
        set(value) {
            field = value
            syncUserScripts()
        }

    /** 页面增强总开关：关闭后不再注入任何脚本 / 规则 / 长按逻辑，用于排查 */
    var pageEnhancements: Boolean = true

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

    private val elementBridge = object {
        @android.webkit.JavascriptInterface
        fun onElement(selector: String, tag: String, imageUrl: String) {
            webView.post {
                onElementLongPress?.invoke(ElementHit(tag, imageUrl, selector))
            }
        }
    }

    /** 打开新标签页（GM_openInTab 用） */
    var onOpenNewTab: ((String) -> Unit)? = null

    private val scriptHandlers = mutableListOf<androidx.webkit.ScriptHandler>()
    private var installedScriptsSignature = ""
    private var prefetchingScripts = false

    init {
        runCatching {
            webView.addJavascriptInterface(
                GmBridge(
                    appContext,
                    { webView },
                    { target -> onOpenNewTab?.invoke(target) },
                    { link, name ->
                        onDownload?.invoke(
                            link,
                            null,
                            if (name.isBlank()) null else "attachment; filename=\"$name\"",
                            null,
                        )
                    },
                ),
                "MafariGM",
            )
        }
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
                syncUserScripts()
            }

            override fun onPageFinished(view: WebView, pageUrl: String?) {
                pageUrl?.let { url = it }
                loading = false
                progress = 1f
                syncNav()
                onVisited?.invoke(url, title)

                injectMissingUserScripts(view)
                injectUserAdRules(view, pageUrl)
                installElementInspector(view)
                // 稍等布局稳定再截标签缩略图
                view.postDelayed({ captureThumbnail(view) }, 350)
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
                if (isUserScriptUrl(target)) {
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

        webView.addJavascriptInterface(elementBridge, "MafariElement")

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
            // 一些站点把 .user.js 以附件形式返回，会走到下载回调；这里改成「导入脚本」
            if (isUserScriptUrl(link)) {
                onUserScriptLink?.invoke(link)
            } else {
                onDownload?.invoke(link, userAgent, contentDisposition, mimeType)
            }
        }

        // 约 2.5cm 的拉动距离，避免误触
        val pullThresholdPx = webView.context.resources.displayMetrics.xdpi / 2.54f * 4f
        var touchDownY = 0f
        var pulled = 0f
        var lastTouchX = 0f
        var lastTouchY = 0f
        webView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    touchDownY = event.y
                    lastTouchX = event.x
                    lastTouchY = event.y
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
            val extra = result.extra
            when {
                result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE && !extra.isNullOrEmpty() -> {
                    onLongPressLink?.invoke(extra)
                    true
                }
                (result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ||
                    result.type == WebView.HitTestResult.IMAGE_TYPE) && !extra.isNullOrEmpty() -> {
                    // 图片链接的 extra 是图片地址（可能是 data: base64），走图片菜单
                    onLongPressImage?.invoke(extra)
                    true
                }
                // 文字/空白区域交还给系统长按选词与页面内 JS 判定
                else -> false
            }
        }
    }

    data class ElementHit(val tag: String, val imageUrl: String, val selector: String)

    /** 只在广告浮层（fixed/absolute 且像广告或面积大）上响应长按，文字区域交给系统选词 */
    private fun installElementInspector(view: WebView) {
        if (!pageEnhancements) return
        val js = """(function(){
  if (window.__mafariInspector) return;
  window.__mafariInspector = true;
  function selectorOf(el){
    try {
      if (el.id) return '#' + CSS.escape(el.id);
      var parts = []; var cur = el;
      while (cur && cur.nodeType === 1 && parts.length < 4) {
        var p = cur.tagName.toLowerCase();
        var cls = (typeof cur.className === 'string' ? cur.className : '').trim().split(/\s+/).filter(Boolean).slice(0, 2);
        for (var k = 0; k < cls.length; k++) { p += '.' + CSS.escape(cls[k]); }
        var parent = cur.parentElement;
        if (parent) { p += ':nth-child(' + (Array.prototype.indexOf.call(parent.children, cur) + 1) + ')'; }
        parts.unshift(p); cur = parent;
      }
      return parts.join(' > ');
    } catch (e) { return ''; }
  }
  function isAdLike(el){
    var st = getComputedStyle(el);
    var pos = st.position;
    if (pos !== 'fixed' && pos !== 'absolute') return false;
    var cls = (typeof el.className === 'string') ? el.className : '';
    var key = (cls + ' ' + (el.id || '')).toLowerCase();
    if (/(^|[\s_-])(ad|ads|advert|banner|popup|float|modal|overlay|mask|sponsor)([\s_-]|$)/.test(key)) return true;
    var r = el.getBoundingClientRect();
    return r.width > innerWidth * 0.5 && r.height > innerHeight * 0.25;
  }
  function fire(el){
    var tag = el.tagName;
    if (tag === 'IMG' || tag === 'INPUT' || tag === 'TEXTAREA') return;
    if (!isAdLike(el)) return;
    var img = el.querySelector ? el.querySelector('img') : null;
    var src = img ? (img.currentSrc || img.src || '') : '';
    try { MafariElement.onElement(selectorOf(el), tag, src); } catch (e) {}
    try { var sel = getSelection(); if (sel) sel.removeAllRanges(); } catch (e) {}
  }
  var timer = null, sx = 0, sy = 0, moved = false;
  document.addEventListener('touchstart', function(e){
    if (e.touches.length !== 1) return;
    var el = e.target;
    if (!el || el.nodeType !== 1) return;
    sx = e.touches[0].clientX; sy = e.touches[0].clientY; moved = false;
    clearTimeout(timer);
    timer = setTimeout(function(){ if (!moved) fire(el); }, 520);
  }, { capture: true, passive: true });
  document.addEventListener('touchmove', function(e){
    if (!e.touches.length) return;
    if (Math.abs(e.touches[0].clientX - sx) > 12 || Math.abs(e.touches[0].clientY - sy) > 12) {
      moved = true; clearTimeout(timer);
    }
  }, { capture: true, passive: true });
  document.addEventListener('touchend', function(){ clearTimeout(timer); }, { capture: true, passive: true });
  document.addEventListener('touchcancel', function(){ clearTimeout(timer); }, { capture: true, passive: true });
})();"""
        view.evaluateJavascript(js, null)
    }

    /** 把用户「标记为广告」记住的选择器注入当前页 */
    private fun injectUserAdRules(view: WebView, pageUrl: String?) {
        if (!pageEnhancements) return
        val host = pageUrl?.let { runCatching { Uri.parse(it).host }.getOrNull() } ?: return
        val selectors = UserAdRules.selectors(appContext, host)
        if (selectors.isEmpty()) return
        val css = selectors.joinToString(",") + "{display:none!important}"
        val js = "(function(){try{var s=document.createElement('style');s.textContent=" +
            org.json.JSONObject.quote(css) +
            ";(document.head||document.documentElement).appendChild(s);}catch(e){}})();"
        view.evaluateJavascript(js, null)
    }

    /**
     * 所有启用的脚本都用 addDocumentStartJavaScript 注入（覆盖 iframe、真正的 document-start），
     * 具体的 URL 匹配与 run-at 等待在运行时（assets/mafari_gm_runtime.js）里按各框架处理。
     * 脚本列表 / 缓存资源变化时重建；系统不支持该特性时由 injectMissingUserScripts 兜底。
     */
    private fun syncUserScripts() {
        if (destroyed) return
        if (!pageEnhancements) {
            scriptHandlers.forEach { handler -> runCatching { handler.remove() } }
            scriptHandlers.clear()
            installedScriptsSignature = ""
            return
        }
        val enabled = userScripts.filter { it.enabled }
        val signature = enabled.joinToString("|") {
            "${it.id}:${it.code.hashCode()}:${it.match.hashCode()}:${ScriptResources.cachedCount(appContext, it.code)}"
        }

        // @require / @resource 还没缓存的先在后台取回来，取到后自动重建（失败的 60 秒后才会重试）
        val needFetch = enabled.filter { ScriptResources.missing(appContext, it.code).isNotEmpty() }
        if (needFetch.isNotEmpty() && !prefetchingScripts) {
            prefetchingScripts = true
            Thread {
                val changed = needFetch.map { ScriptResources.prefetch(appContext, it.code) }.any { it }
                webView.post {
                    prefetchingScripts = false
                    if (changed) syncUserScripts()
                }
            }.start()
        }
        if (signature == installedScriptsSignature) return

        scriptHandlers.forEach { handler -> runCatching { handler.remove() } }
        scriptHandlers.clear()
        installedScriptsSignature = signature
        if (!androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return
        }
        enabled.forEach { script ->
            runCatching {
                val source = UserscriptEngine.buildSource(appContext, script)
                androidx.webkit.WebViewCompat.addDocumentStartJavaScript(webView, source, setOf("*"))
                    ?.let { scriptHandlers.add(it) }
            }.onFailure { Diag.log("userscript: install failed ${script.name}: ${it.message}") }
        }
        Diag.log("userscript: document-start handlers=${scriptHandlers.size}/${enabled.size}")
    }

    /**
     * 兜底：页面加载完后问一下页面里已经「见过」哪些脚本（运行时一进来就登记），
     * 没见过的（特性不支持、或 handler 安装晚于本次导航）再用 evaluateJavascript 补注入主框架。
     */
    private fun injectMissingUserScripts(view: WebView) {
        if (!pageEnhancements) return
        val enabled = userScripts.filter { it.enabled }
        if (enabled.isEmpty()) return
        val query = "(function(){try{return JSON.stringify(Object.keys(window.__mafariSeen||{}))}catch(e){return '[]'}})()"
        view.evaluateJavascript(query) { raw ->
            val seen = runCatching {
                val text = org.json.JSONTokener(raw ?: "null").nextValue() as? String ?: "[]"
                val array = org.json.JSONArray(text)
                (0 until array.length()).map { array.getString(it) }.toSet()
            }.getOrDefault(emptySet())
            val pending = enabled.filter { it.id !in seen }
            pending.forEach { script ->
                runCatching { view.evaluateJavascript(UserscriptEngine.buildSource(appContext, script), null) }
            }
            Diag.log("userscript: page=$url injected-late=${pending.size}/${enabled.size}")
        }
    }

    /** 当前页面里脚本通过 GM_registerMenuCommand 注册的命令（仅主框架） */
    fun queryMenuCommands(onResult: (List<ScriptMenuCommand>) -> Unit) {
        val js = "(function(){try{return window.__mafariMenuList?window.__mafariMenuList():'[]'}catch(e){return '[]'}})()"
        webView.evaluateJavascript(js) { raw ->
            val list = runCatching {
                val text = org.json.JSONTokener(raw ?: "null").nextValue() as? String ?: "[]"
                val array = org.json.JSONArray(text)
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    ScriptMenuCommand(o.optString("id"), o.optString("title"), o.optString("script"))
                }
            }.getOrDefault(emptyList())
            onResult(list)
        }
    }

    fun runMenuCommand(id: String) {
        webView.evaluateJavascript(
            "(function(){try{window.__mafariMenuRun&&window.__mafariMenuRun(" + org.json.JSONObject.quote(id) + ")}catch(e){}})()",
            null,
        )
    }

    private fun syncNav() {
        canGoBack = runCatching { webView.canGoBack() }.getOrDefault(false) || fromHome ||
            (url.isEmpty() && savedUrl.isNotBlank())
        canGoForward = runCatching { webView.canGoForward() }.getOrDefault(false)
    }

    fun load(input: String) {
        val target = toUrl(input, searchTemplate)
        if (url.isEmpty()) fromHome = true
        url = target
        val headers = extraRequestHeaders()
        if (headers.isEmpty()) {
            webView.loadUrl(target)
        } else {
            webView.loadUrl(target, headers)
        }
        syncNav()
    }

    fun loadUrl(raw: String) {
        url = raw
        val headers = extraRequestHeaders()
        if (headers.isEmpty()) {
            webView.loadUrl(raw)
        } else {
            webView.loadUrl(raw, headers)
        }
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

    /**
     * 应用隐私设置：第三方 Cookie、DNT 请求头、WebRTC 禁用。
     * DNT 通过附加请求头实现；WebRTC 通过关闭媒体源间接限制（WebView 无直接开关时尽力而为）。
     */
    fun applyPrivacy(
        thirdPartyCookiesAllowed: Boolean,
        sendDnt: Boolean,
        webrtcDisabled: Boolean,
    ) {
        runCatching {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, thirdPartyCookiesAllowed)
        }
        dntEnabled = sendDnt
        // WebRTC：关闭混合内容与媒体播放授权，降低 IP 泄露面
        runCatching {
            if (webrtcDisabled) {
                webView.settings.mediaPlaybackRequiresUserGesture = true
            }
        }
        this.webrtcDisabledFlag = webrtcDisabled
    }

    @Volatile
    private var dntEnabled = false

    @Volatile
    private var webrtcDisabledFlag = false

    /** 供 WebViewClient 附加 DNT 头 */
    fun extraRequestHeaders(): Map<String, String> =
        if (dntEnabled) {
            mapOf("DNT" to "1", "Sec-GPC" to "1")
        } else {
            emptyMap()
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

    /** 截取当前页面作为标签缩略图（须在主线程调用） */
    private fun captureThumbnail(view: WebView) {
        if (closed || destroyed || url.isBlank() || url == "about:blank") return
        runCatching {
            if (view.width <= 0 || view.height <= 0) return
            val bitmap = android.graphics.Bitmap.createBitmap(
                view.width.coerceAtMost(720),
                view.height.coerceAtMost(1280),
                android.graphics.Bitmap.Config.ARGB_8888,
            )
            val canvas = android.graphics.Canvas(bitmap)
            val scaleX = bitmap.width.toFloat() / view.width
            val scaleY = bitmap.height.toFloat() / view.height
            canvas.scale(scaleX, scaleY)
            view.draw(canvas)
            thumbnail = bitmap.asImageBitmap()
        }
    }

    fun findNext(forward: Boolean) {
        runCatching { webView.findNext(forward) }
    }

    fun clearFind() {
        runCatching { webView.clearMatches() }
    }

    /** 回主页 */
    private var savedUrl: String = ""

    fun goHome() {
        savedUrl = url
        fromHome = false
        url = ""
        errorText = null
        loading = false
        syncNav()
    }

    fun goBack() {
        // 从主页返回：恢复刚才那页（WebView 没销毁，直接还原状态即可）
        if (url.isEmpty() && savedUrl.isNotBlank()) {
            url = savedUrl
            savedUrl = ""
            syncNav()
            return
        }
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

    fun newTab(target: String = "", incognito: Boolean = false) {
        if (tabs.size >= MAX_TABS) {
            android.widget.Toast.makeText(
                context,
                "最多同时打开 $MAX_TABS 个标签页",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val tab = BrowserTab(context)
        tab.customUserAgent = userAgent
        tab.applyUserAgent()
        tab.applyIncognito(incognito)
        tabs.add(tab)
        activeIndex = tabs.lastIndex
        if (target.isNotEmpty()) tab.load(target)
    }

    /** 当前标签是否无痕 */
    val activeIncognito: Boolean
        get() = activeTab.incognito

    /** 切换当前标签无痕状态（无痕时新开标签，已有无痕则关闭回普通） */
    fun toggleIncognito() {
        if (activeTab.incognito) {
            // 回到普通：关闭当前无痕标签，必要时新建普通标签
            closeTab(activeIndex)
            if (tabs.isEmpty() || !tabs.any { !it.incognito }) {
                newTab(incognito = false)
            } else {
                val normal = tabs.indexOfFirst { !it.incognito }
                if (normal >= 0) select(normal)
            }
        } else {
            newTab(incognito = true)
        }
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
        if (tabs.size >= MAX_TABS) {
            android.widget.Toast.makeText(
                context,
                "最多同时打开 $MAX_TABS 个标签页",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val tab = BrowserTab(context)
        tabs.add(tab)
        tab.load(target)
    }

    /**
     * 清空指定类型的标签页。普通与无痕标签互不影响。
     * 若当前活动标签被清除，则切换到剩余标签；只有全部标签都被清除时才创建普通空白页。
     */
    fun closeTabs(incognito: Boolean) {
        if (tabs.isEmpty()) return
        val active = tabs.getOrNull(activeIndex)
        val removeIndices = tabs.indices.filter { tabs[it].incognito == incognito }
        if (removeIndices.isEmpty()) return

        removeIndices.asReversed().forEach { index ->
            tabs.removeAt(index).close()
        }

        if (tabs.isEmpty()) {
            activeIndex = 0
            newTab(incognito = false)
            return
        }

        val preservedIndex = active?.let { tabs.indexOf(it) } ?: -1
        activeIndex = if (preservedIndex >= 0) {
            preservedIndex
        } else {
            // 被清空的是当前活动分类时，优先切换到剩余的普通标签，再回退到首个标签。
            tabs.indexOfFirst { !it.incognito }.takeIf { it >= 0 } ?: 0
        }
    }

    fun closeAll() {
        tabs.forEach { it.close() }
        tabs.clear()
        newTab()
    }

    companion object {
        const val MAX_TABS = 99
    }
}
