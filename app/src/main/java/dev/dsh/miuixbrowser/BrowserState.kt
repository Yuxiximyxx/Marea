package dev.dsh.miuixbrowser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceResponse
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
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
        // Cookie 开关是进程级的，不能在这里改；改为在该标签成为当前标签时由 applyPrivacy 设置
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

    /** 网页增强总开关：关闭后不再注入任何脚本 / 长按逻辑，用于排查（去广告有自己的开关，见 AdBlock） */
    var pageEnhancements: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            syncUserScripts()
        }

    /** 搜索模板，由设置注入 */
    var searchTemplate: String = "https://www.baidu.com/s?wd=%s"

    /** 下拉进度 0..1，用于顶部动画 */
    var pullProgress by mutableFloatStateOf(0f)
        private set

    /** 下载请求回调（交给界面弹确认框）。blob/data 由 extractBlob 抽取后走 onBlobReady。 */
    var onDownload: ((url: String, userAgent: String?, disposition: String?, mime: String?) -> Unit)? = null

    /** 页面要求选文件 / 拍照 / 录像：由界面拉起系统选择器或相机 */
    var onShowFileChooser: (
        params: WebChromeClient.FileChooserParams,
        callback: ValueCallback<Array<Uri>>,
    ) -> Unit = { _, callback -> callback.onReceiveValue(null) }

    /** HTML5 视频全屏：把 WebView 给的 View 挂到全屏容器 */
    var onShowCustomView: ((view: View, callback: WebChromeClient.CustomViewCallback) -> Unit)? = null

    /** 退出全屏（视频结束 / 返回键） */
    var onHideCustomView: (() -> Unit)? = null

    /** blob / data URL 抽取完成：dataUri 或 tempPath 二选一（大文件走 tempPath） */
    var onBlobReady: ((dataUrl: String?, fileName: String, mime: String?, tempPath: String?) -> Unit)? = null

    /** 大 blob 开始读取（界面可提示“正在读取”） */
    var onBlobStart: ((fileName: String) -> Unit)? = null

    /** blob 抽取失败 */
    var onBlobError: ((message: String) -> Unit)? = null

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

    /**
     * blob 分片接收：evaluateJavascript 返回值有大小上限，大文件必须走 JS→桥接分片。
     *
     * 每次 [extractBlob] 在原生侧创建一个独立会话（一次性 token + 独立缓存文件）：
     * - 桥接方法全部要求 token，网页自己调用 MafariBlob 拿不到 token，无法伪造下载或写缓存文件；
     * - 多个 blob 并发抽取互不干扰；
     * - 写入出错会记入会话，end 时校验总字节数，残缺文件不会被当成成功。
     */
    private class BlobSession(
        val file: java.io.File,
        val stream: java.io.FileOutputStream,
        var name: String,
        var mime: String?,
    ) {
        var written = 0L
        var failed = false
        var finished = false
    }

    private val blobSessions = java.util.concurrent.ConcurrentHashMap<String, BlobSession>()

    private fun discardBlobSession(token: String) {
        val session = blobSessions.remove(token) ?: return
        synchronized(session) {
            runCatching { session.stream.close() }
            runCatching { session.file.delete() }
        }
    }

    private fun discardAllBlobSessions() {
        blobSessions.keys.toList().forEach { discardBlobSession(it) }
    }

    private fun reportBlobError(message: String) {
        webView.post { onBlobError?.invoke(message) }
    }

    private val blobBridge = object {
        /** 返回 false 表示原生侧已拒绝（token 无效 / 空间不足），JS 端应直接停止。 */
        @android.webkit.JavascriptInterface
        fun begin(token: String, mime: String, size: Double): Boolean {
            val session = blobSessions[token] ?: return false
            if (size > 0 && size > appContext.cacheDir.usableSpace - MIN_FREE_BYTES) {
                discardBlobSession(token)
                reportBlobError("存储空间不足，无法读取该文件")
                return false
            }
            if (mime.isNotBlank()) session.mime = mime
            if (size >= LARGE_BLOB_NOTICE_BYTES) {
                val label = session.name
                webView.post { onBlobStart?.invoke(label) }
            }
            return true
        }

        @android.webkit.JavascriptInterface
        fun appendChunk(token: String, b64: String) {
            val session = blobSessions[token] ?: return
            try {
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                synchronized(session) {
                    if (session.finished || session.failed) return
                    session.stream.write(bytes)
                    session.written += bytes.size
                }
            } catch (e: Exception) {
                session.failed = true
                Diag.log("blob append failed: ${e.message}")
            }
        }

        @android.webkit.JavascriptInterface
        fun end(token: String, total: Double) {
            val session = blobSessions.remove(token) ?: return
            val ok: Boolean
            synchronized(session) {
                session.finished = true
                runCatching { session.stream.flush(); session.stream.close() }
                ok = !session.failed && session.written > 0L &&
                    (total < 0 || session.written == total.toLong()) &&
                    session.file.exists() && session.file.length() == session.written
            }
            if (!ok) {
                runCatching { session.file.delete() }
                reportBlobError("文件读取不完整或写入失败")
                return
            }
            val name = Downloader.withExtension(session.name, session.mime)
            val mime = session.mime
            val path = session.file.absolutePath
            webView.post { onBlobReady?.invoke(null, name, mime, path) }
        }

        @android.webkit.JavascriptInterface
        fun fail(token: String, msg: String) {
            if (!blobSessions.containsKey(token)) return
            discardBlobSession(token)
            reportBlobError("无法读取 blob：$msg")
        }
    }

    /** 打开新标签页（GM_openInTab 用） */
    var onOpenNewTab: ((String) -> Unit)? = null

    private val scriptHandlers = mutableListOf<androidx.webkit.ScriptHandler>()
    private var installedScriptsSignature = ""
    private var prefetchingScripts = false

    /** 文件选择器：WebView 持有的回调，界面拉起选择器后必须回传（null = 取消） */
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null

    /** HTML5 视频全屏中的 View 与回调 */
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    /** 界面取消文件选择（返回键 / 关闭弹层） */
    fun cancelFileChooser() {
        val cb = pendingFileCallback
        pendingFileCallback = null
        runCatching { cb?.onReceiveValue(null) }
    }

    /** 是否正在全屏自定义视图 */
    val isCustomViewShowing: Boolean get() = customView != null

    /** 退出视频全屏（返回键调用） */
    fun exitCustomView() {
        val cb = customViewCallback
        if (cb == null && customView == null) return
        customView = null
        customViewCallback = null
        restoreOrientation()
        onHideCustomView?.invoke()
        runCatching { cb?.onCustomViewHidden() }
    }

    /** 全屏视频：强制横屏 */
    private fun lockLandscape() {
        val activity = resolveActivity() ?: return
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    /** 退出全屏：恢复系统方向 */
    private fun restoreOrientation() {
        val activity = resolveActivity() ?: return
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    /** LocalContext 可能是 ContextThemeWrapper，逐层解开找 Activity */
    private fun resolveActivity(): android.app.Activity? {
        var c: Context? = appContext
        while (c != null) {
            if (c is android.app.Activity) return c
            c = (c as? android.content.ContextWrapper)?.baseContext
        }
        return null
    }

    /**
     * 把 blob 分片写入缓存文件（不经 evaluateJavascript 返回大字符串）。
     * 取 Blob 顺序：createObjectURL 缓存（含同源 iframe）→ XHR → fetch。
     * 完成后回调 onBlobReady(tempPath=...)。
     */
    fun extractBlob(
        url: String,
        userAgent: String? = null,
        disposition: String? = null,
        mime: String? = null,
    ) {
        val fileName = Downloader.fileNameFrom(url, disposition)
        val preferredMime = mime ?: "application/octet-stream"
        val token = java.util.UUID.randomUUID().toString()
        val file = java.io.File(appContext.cacheDir, "mafari_blob_${token}.part")
        val stream = runCatching { java.io.FileOutputStream(file) }.getOrElse {
            reportBlobError("无法创建缓存文件")
            return
        }
        blobSessions[token] = BlobSession(file, stream, fileName, mime?.takeIf { it.isNotBlank() && it != "application/octet-stream" })
        // 分片 256KB 二进制 → base64 约 350KB，避开 JS 桥接/字符串上限
        val js = """
            (function(){
              var B = window.MafariBlob;
              var token = ${org.json.JSONObject.quote(token)};
              var url = ${org.json.JSONObject.quote(url)};
              var preferredMime = ${org.json.JSONObject.quote(preferredMime)};
              var CHUNK = 256 * 1024;
              if (!B) return;

              function findCached(u) {
                try {
                  if (window.__mafariBlobCache && window.__mafariBlobCache[u]) {
                    return window.__mafariBlobCache[u];
                  }
                } catch (e) {}
                try {
                  var list = document.querySelectorAll('iframe');
                  for (var i = 0; i < list.length; i++) {
                    try {
                      var w = list[i].contentWindow;
                      if (w && w.__mafariBlobCache && w.__mafariBlobCache[u]) {
                        return w.__mafariBlobCache[u];
                      }
                    } catch (e) {}
                  }
                } catch (e) {}
                return null;
              }

              function viaXhr(u) {
                return new Promise(function(resolve, reject) {
                  try {
                    var x = new XMLHttpRequest();
                    x.open('GET', u, true);
                    x.responseType = 'blob';
                    x.onload = function() {
                      if (x.response && x.response.size >= 0) resolve(x.response);
                      else reject(new Error('xhr empty'));
                    };
                    x.onerror = function() { reject(new Error('xhr error')); };
                    x.send();
                  } catch (e) { reject(e); }
                });
              }

              function viaFetch(u) {
                return fetch(u).then(function(r){ return r.blob(); });
              }

              function getBlob(u) {
                var c = findCached(u);
                if (c) return Promise.resolve(c);
                return viaXhr(u).catch(function(){ return viaFetch(u); });
              }

              getBlob(url).then(function(b){
                if (!b || !b.size) throw new Error('empty blob');
                if (!B.begin(token, b.type || preferredMime, b.size)) return;
                var i = 0;
                function next(){
                  if (i >= b.size) { B.end(token, b.size); return; }
                  var end = Math.min(i + CHUNK, b.size);
                  var slice = b.slice(i, end);
                  i = end;
                  return slice.arrayBuffer().then(function(buf){
                    var bytes = new Uint8Array(buf);
                    var bin = '';
                    var BL = 0x8000;
                    for (var j = 0; j < bytes.length; j += BL) {
                      bin += String.fromCharCode.apply(null, bytes.subarray(j, j + BL));
                    }
                    B.appendChunk(token, btoa(bin));
                    return next();
                  });
                }
                return next();
              }).catch(function(e){ B.fail(token, String(e && e.message || e)); });
            })()
        """.trimIndent()
        // 结果由 blobBridge 回调，这里不解析返回值
        webView.evaluateJavascript(js, null)
    }

    /** 去广告元素隐藏脚本是否已作为 document-start 脚本装上（没装上时由页面加载回调兜底注入） */
    private var adblockDocStart = false

    init {
        runCatching { webView.addJavascriptInterface(AdBlockBridge(), "MafariAdblock") }
        runCatching { webView.addJavascriptInterface(blobBridge, "MafariBlob") }
        cleanStaleBlobFiles(appContext)
        runCatching {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
                // 社区通用：缓存 createObjectURL 的 Blob，extractBlob 直接读，避免 fetch blob: 失败
                runCatching {
                    val cacheJs = appContext.assets.open("mafari_blob_cache.js")
                        .bufferedReader(Charsets.UTF_8).use { it.readText() }
                    androidx.webkit.WebViewCompat.addDocumentStartJavaScript(webView, cacheJs, setOf("*"))
                }.onFailure { Diag.log("blob cache install failed: ${it.message}") }
                androidx.webkit.WebViewCompat.addDocumentStartJavaScript(webView, AdBlock.COSMETIC_JS, setOf("*"))
                adblockDocStart = true
            }
        }.onFailure { Diag.log("adblock: document-start install failed ${it.message}") }
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
        // 先固定系统默认 UA（之后 applyUserAgent 会覆盖 settings.userAgentString）
        runCatching { defaultUserAgent.length }
        // 打开 WebView 的 WebAuthn（Passkey）支持。
        // 注意：之前这里传的是 SUPPORT_NONE，等于主动关闭了 Passkey。
        // 浏览器需要对任意网站的 origin 生效 → 优先 FOR_BROWSER；
        // 若 WebView / 系统不接受（读回仍是 NONE），退回 FOR_APP。
        runCatching {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.WEB_AUTHENTICATION)) {
                val settings = webView.settings
                androidx.webkit.WebSettingsCompat.setWebAuthenticationSupport(
                    settings,
                    androidx.webkit.WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER,
                )
                var mode = androidx.webkit.WebSettingsCompat.getWebAuthenticationSupport(settings)
                if (mode == androidx.webkit.WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_NONE) {
                    androidx.webkit.WebSettingsCompat.setWebAuthenticationSupport(
                        settings,
                        androidx.webkit.WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP,
                    )
                    mode = androidx.webkit.WebSettingsCompat.getWebAuthenticationSupport(settings)
                }
                Diag.log("passkey: WebAuthn support mode=$mode")
            } else {
                Diag.log("passkey: WEB_AUTHENTICATION not supported by this WebView")
            }
        }.onFailure { Diag.log("passkey: enable failed ${it.message}") }
        // 默认浅色底；深浅色由 applyDisplayTheme 按应用主题覆盖
        webView.setBackgroundColor(0xFFFFFFFF.toInt())
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, pageUrl: String?, favicon: Bitmap?) {
                pageUrl?.let { url = it }
                pageHost = runCatching { Uri.parse(pageUrl).host }.getOrNull().orEmpty()
                loading = true
                errorText = null
                syncNav()
                syncUserScripts()
                if (!adblockDocStart) view.evaluateJavascript(AdBlock.COSMETIC_JS, null)
            }

            /** HTTP 代理认证：挑战主机等于代理主机时自动提交设置里的账号密码 */
            override fun onReceivedHttpAuthRequest(
                view: WebView?,
                handler: android.webkit.HttpAuthHandler?,
                host: String?,
                realm: String?,
            ) {
                if (handler == null) return
                if (host != null && WebViewProxy.shouldAutoAuth(host)) {
                    handler.proceed(WebViewProxy.authUser, WebViewProxy.authPass)
                } else {
                    handler.cancel()
                }
            }

            override fun onPageFinished(view: WebView, pageUrl: String?) {
                pageUrl?.let { url = it }
                loading = false
                progress = 1f
                syncNav()
                onVisited?.invoke(url, title)

                injectMissingUserScripts(view)
                if (!adblockDocStart) view.evaluateJavascript(AdBlock.COSMETIC_JS, null)
                installElementInspector(view)
                // 稍等布局稳定再截标签缩略图
                view.postDelayed({ captureThumbnail(view) }, 350)
            }

            override fun doUpdateVisitedHistory(view: WebView, pageUrl: String?, isReload: Boolean) {
                pageUrl?.let { url = it }
                syncNav()
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                AdBlock.intercept(request, pageHost, blockTrackers)

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
                // DNT / Sec-GPC：点击链接产生的主框架 GET 导航也带上请求头
                if (dntEnabled && (scheme == "http" || scheme == "https") && request.isForMainFrame &&
                    request.method == "GET" && !request.isRedirect &&
                    request.requestHeaders["DNT"] == null && target != lastDntUrl
                ) {
                    lastDntUrl = target
                    view.loadUrl(target, extraRequestHeaders())
                    return true
                }
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

            override fun onShowFileChooser(
                view: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean {
                if (filePathCallback == null || fileChooserParams == null) return false
                // WebView 可能在上一次未回调时再次请求，先取消上一次
                cancelFileChooser()
                pendingFileCallback = filePathCallback
                this@BrowserTab.onShowFileChooser.invoke(fileChooserParams, filePathCallback)
                return true
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null || callback == null) return
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                lockLandscape()
                this@BrowserTab.onShowCustomView?.invoke(view, callback)
            }

            override fun onHideCustomView() {
                val cb = customViewCallback
                customView = null
                customViewCallback = null
                restoreOrientation()
                this@BrowserTab.onHideCustomView?.invoke()
                // 界面摘掉 View 后必须通知 WebView
                cb?.onCustomViewHidden()
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                webView.post { handleWebPermission(request) }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback,
            ) {
                webView.post {
                    decide(
                        locationMode,
                        Uri.parse(origin).host ?: origin,
                        "获取你的位置",
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                        anyOk = true,
                    ) { allow -> callback.invoke(origin, allow, false) }
                }
            }
        }

        webView.setDownloadListener { link, userAgent, contentDisposition, mimeType, _ ->
            // 一些站点把 .user.js 以附件形式返回，会走到下载回调；这里改成「导入脚本」
            when {
                isUserScriptUrl(link) -> onUserScriptLink?.invoke(link)
                // blob：HTTP 下载器拉不到，走页面内抽取
                link.startsWith("blob:", ignoreCase = true) ->
                    extractBlob(link, userAgent, contentDisposition, mimeType)
                // data URL 附件：直接交给界面落盘
                link.startsWith("data:", ignoreCase = true) -> {
                    val dataMime = link.substringAfter("data:").substringBefore(',').substringBefore(';')
                        .ifBlank { null }
                    val name = Downloader.withExtension(
                        Downloader.fileNameFrom(link, contentDisposition),
                        mimeType?.takeIf { it.isNotBlank() } ?: dataMime,
                    )
                    onBlobReady?.invoke(link, name, mimeType ?: dataMime, null)
                }
                else -> onDownload?.invoke(link, userAgent, contentDisposition, mimeType)
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

    /** 当前页面（主框架）里真正匹配并运行的脚本数量 */
    fun queryRunningScripts(onResult: (Int) -> Unit) {
        val js = "(function(){try{return Object.keys(window.__mafariRan||{}).length}catch(e){return 0}})()"
        webView.evaluateJavascript(js) { raw -> onResult(raw?.trim()?.toIntOrNull() ?: 0) }
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
            desktopMode -> desktopUserAgent()
            customUserAgent.isBlank() || customUserAgent == DESKTOP_UA -> defaultUserAgent
            else -> customUserAgent
        }
        runCatching { webView.settings.userAgentString = ua }
    }

    /**
     * 显示模式深浅色同步到 WebView（公开 API，不自造 CSS 覆盖）：
     * 1. `WebSettings.setAlgorithmicDarkeningAllowed` — 系统/WebView 官方深色适配（API 33+）
     * 2. `setBackgroundColor` — 空白/加载底色跟随应用主题
     * 3. 对齐 Activity `Configuration.uiMode` — 让算法深色跟随应用内「深色/浅色/跟随系统」
     */
    fun applyDisplayTheme(dark: Boolean) {
        runCatching { webView.settings.setAlgorithmicDarkeningAllowed(true) }
        webView.setBackgroundColor(
            if (dark) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt(),
        )
        val activity = resolveActivity() ?: return
        runCatching {
            val res = activity.resources
            val conf = res.configuration
            val night = if (dark) {
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            } else {
                android.content.res.Configuration.UI_MODE_NIGHT_NO
            }
            if ((conf.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) != night) {
                val next = android.content.res.Configuration(conf)
                next.uiMode = (next.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                @Suppress("DEPRECATION")
                res.updateConfiguration(next, res.displayMetrics)
            }
        }
    }

    /**
     * 应用隐私与权限设置（当前标签成为活动标签、或设置变化时调用）。
     * - 第三方 Cookie：CookieManager 按 WebView 设置；Cookie 总开关随标签是否无痕切换。
     * - DNT：请求头（地址栏 / 链接点击的主框架导航）+ navigator.doNotTrack / globalPrivacyControl。
     * - WebRTC / 剪贴板 / 媒体静音：document-start 脚本注入所有框架，刷新页面后生效。
     * - 摄像头 / 麦克风 / 定位：在 WebChromeClient 里按「询问 / 允许 / 拒绝」处理。
     * - 跟踪脚本：shouldInterceptRequest 拦截第三方统计域名（由去广告总开关控制，见 AdBlock）。
     */
    fun applyPrivacy(
        thirdPartyCookiesAllowed: Boolean,
        sendDnt: Boolean,
        webrtcDisabled: Boolean,
        clipboardMode: Int = 0,
        mediaMode: Int = 1,
        cameraMode: Int = 0,
        micMode: Int = 0,
        locationMode: Int = 0,
        notificationMode: Int = 0,
        blockTrackers: Boolean = false,
    ) {
        runCatching {
            CookieManager.getInstance().setAcceptCookie(!incognito)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, thirdPartyCookiesAllowed)
        }
        dntEnabled = sendDnt
        this.webrtcDisabledFlag = webrtcDisabled
        this.cameraMode = cameraMode
        this.micMode = micMode
        this.locationMode = locationMode
        this.notificationMode = notificationMode
        this.blockTrackers = blockTrackers
        // 媒体发声：允许=可自动播放；询问/拒绝=需用户手势才能出声
        runCatching { webView.settings.mediaPlaybackRequiresUserGesture = mediaMode != 1 }

        val script = buildString {
            if (webrtcDisabled) {
                runCatching {
                    append(appContext.assets.open("mafari_no_webrtc.js").bufferedReader(Charsets.UTF_8).use { it.readText() })
                    append('\n')
                }
            }
            if (clipboardMode == 2) {
                append(
                    "(function(){try{var d=function(){return Promise.reject(new DOMException('Permission denied','NotAllowedError'))};" +
                        "if(navigator.clipboard){['read','readText','write','writeText'].forEach(function(m){try{navigator.clipboard[m]=d}catch(e){}})}}catch(e){}})();\n",
                )
            }
            if (sendDnt) {
                append(
                    "(function(){try{Object.defineProperty(Navigator.prototype,'doNotTrack',{get:function(){return '1'},configurable:true});" +
                        "Object.defineProperty(Navigator.prototype,'globalPrivacyControl',{get:function(){return true},configurable:true})}catch(e){}})();\n",
                )
            }
            if (mediaMode == 2) {
                append(
                    "(function(){try{Object.defineProperty(HTMLMediaElement.prototype,'muted',{get:function(){return true},set:function(v){},configurable:true});" +
                        "var p=HTMLMediaElement.prototype.play;HTMLMediaElement.prototype.play=function(){try{this.volume=0}catch(e){}return p.apply(this,arguments)}}catch(e){}})();\n",
                )
            }
            // 网站通知拒绝：JS Notification 权限固定为 denied
            if (notificationMode == 2) {
                append(
                    "(function(){try{" +
                        "if(window.Notification){Object.defineProperty(Notification,'permission',{get:function(){return 'denied'},configurable:true});" +
                        "Notification.requestPermission=function(){return Promise.resolve('denied')}}}" +
                        "}catch(e){}})();\n",
                )
            }
        }
        runCatching { privacyHandler?.remove() }
        privacyHandler = null
        if (script.isNotBlank() &&
            androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            runCatching {
                privacyHandler = androidx.webkit.WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
            }.onFailure { Diag.log("privacy script install failed: ${it.message}") }
        }
    }

    private var privacyHandler: androidx.webkit.ScriptHandler? = null

    @Volatile
    private var blockTrackers = false

    @Volatile
    private var pageHost = ""

    private var lastDntUrl = ""

    private var cameraMode = 0
    private var micMode = 0
    private var locationMode = 0
    private var notificationMode = 0

    /** 网页权限「询问」：由界面弹窗，decide(true/false) 只能调用一次 */
    var onPermissionPrompt: ((origin: String, what: String, decide: (Boolean) -> Unit) -> Unit)? = null

    /** 申请 Android 运行时权限，完成后回调（调用方再自行检查授予结果） */
    var requestAndroidPermissions: ((Array<String>, () -> Unit) -> Unit)? = null

    private fun hasPermission(permission: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(appContext, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun ensureAndroid(perms: Array<String>, anyOk: Boolean, result: (Boolean) -> Unit) {
        val need = perms.filter { it.isNotBlank() && !hasPermission(it) }.toTypedArray()
        fun granted() = if (anyOk) {
            perms.filter { it.isNotBlank() }.any { hasPermission(it) }
        } else {
            perms.filter { it.isNotBlank() }.all { hasPermission(it) }
        }
        if (need.isEmpty()) {
            result(granted())
            return
        }
        val request = requestAndroidPermissions
        if (request == null) {
            result(false)
        } else {
            request(need) { result(granted()) }
        }
    }

    private fun decide(
        mode: Int,
        origin: String,
        what: String,
        perms: Array<String>,
        anyOk: Boolean = false,
        result: (Boolean) -> Unit,
    ) {
        when (mode) {
            2 -> result(false)
            1 -> ensureAndroid(perms, anyOk, result)
            else -> {
                val prompt = onPermissionPrompt
                if (prompt == null) {
                    result(false)
                } else {
                    prompt(origin, what) { allow ->
                        if (allow) ensureAndroid(perms, anyOk, result) else result(false)
                    }
                }
            }
        }
    }

    private fun handleWebPermission(request: PermissionRequest) {
        // Android SDK 的 PermissionRequest 仅有：
        // RESOURCE_AUDIO_CAPTURE / RESOURCE_VIDEO_CAPTURE / RESOURCE_PROTECTED_MEDIA_ID / RESOURCE_MIDI_SYSEX
        // 定位走 onGeolocationPermissionsShowPrompt；网站通知 WebView 不走 PermissionRequest（设置里用 JS 拒绝生效）
        val res = request.resources.toSet()
        val keep = mutableListOf<String>()
        if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID in res) keep += PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
        val camOk = PermissionRequest.RESOURCE_VIDEO_CAPTURE in res && cameraMode != 2
        val micOk = PermissionRequest.RESOURCE_AUDIO_CAPTURE in res && micMode != 2
        fun finish(extra: List<String>) {
            val all = keep + extra
            if (all.isEmpty()) request.deny() else request.grant(all.toTypedArray())
        }
        if (!camOk && !micOk) {
            finish(emptyList())
            return
        }
        val perms = buildList {
            if (camOk) add(android.Manifest.permission.CAMERA)
            if (micOk) add(android.Manifest.permission.RECORD_AUDIO)
        }.toTypedArray()
        val what = listOfNotNull(if (camOk) "摄像头" else null, if (micOk) "麦克风" else null).joinToString("、")
        val mustAsk = (camOk && cameraMode == 0) || (micOk && micMode == 0)
        decide(if (mustAsk) 0 else 1, request.origin.host ?: request.origin.toString(), "使用$what", perms) { ok ->
            finish(
                if (ok) buildList {
                    if (camOk) add(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                    if (micOk) add(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                } else emptyList(),
            )
        }
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

    /** 与本机 WebView 内核主版本一致的桌面 Chrome UA（遵循 UA 精简格式 Chrome/主版本.0.0.0） */
    private fun desktopUserAgent(): String {
        val major = Regex("Chrome/(\\d+)").find(defaultUserAgent)?.groupValues?.get(1)
            ?: return DESKTOP_UA
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
    }

    private var desktopHandler: androidx.webkit.ScriptHandler? = null

    /** 安装 / 移除桌面模式脚本（viewport 重写、platform / 触控 / 媒体查询伪装），随该标签所有导航生效 */
    private fun installDesktopScript(enabled: Boolean) {
        runCatching { desktopHandler?.remove() }
        desktopHandler = null
        if (!enabled) return
        if (!androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) return
        runCatching {
            val script = appContext.assets.open("mafari_desktop.js")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            desktopHandler = androidx.webkit.WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        }.onFailure { Diag.log("desktop script install failed: ${it.message}") }
    }

    fun toggleDesktopMode(enabled: Boolean) {
        desktopMode = enabled
        runCatching {
            webView.settings.apply {
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                // 桌面模式关掉文字自动放大，保持桌面排版；退出后恢复
                layoutAlgorithm = if (enabled) {
                    WebSettings.LayoutAlgorithm.NORMAL
                } else {
                    WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                }
            }
        }
        installDesktopScript(enabled)
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
            // 卡片只有 ~180dp 宽且叠了 0.45 透明度，缩到 480x854 以内足够；
            // 原来 720x1280 ARGB 每张 3.5MB，多标签时绘制/上传纹理都很重。
            val scale = minOf(480f / view.width, 854f / view.height, 1f)
            val bitmap = android.graphics.Bitmap.createBitmap(
                (view.width * scale).toInt().coerceAtLeast(1),
                (view.height * scale).toInt().coerceAtLeast(1),
                android.graphics.Bitmap.Config.ARGB_8888,
            )
            val canvas = android.graphics.Canvas(bitmap)
            canvas.scale(scale, scale)
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

    /** 标签实际承载的页面地址：回过主页的标签 url 为空，页面还在 WebView 里，用 savedUrl */
    val effectiveUrl: String
        get() = url.ifEmpty { savedUrl }

    /** 完全空白的标签（没有页面、也没有被主页盖住的页面） */
    val isBlank: Boolean
        get() = url.isEmpty() && savedUrl.isBlank()

    /** 若这个标签正显示主页（页面被盖住），还原成原来的页面，不重新加载 */
    fun restoreFromHome() {
        if (url.isEmpty() && savedUrl.isNotBlank()) {
            url = savedUrl
            savedUrl = ""
            syncNav()
        }
    }

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
        cancelFileChooser()
        exitCustomView()
        discardAllBlobSessions()
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

    /** 站点标识：小写 host，忽略开头的 www. */
    private fun siteKey(url: String): String? {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()
        if (host.isNullOrBlank()) return null
        return host.removePrefix("www.")
    }

    /** 是否站点根地址（无路径/查询/片段） */
    private fun isSiteRoot(url: String): Boolean {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return true
        val path = u.path.orEmpty()
        return (path.isEmpty() || path == "/") && u.query.isNullOrEmpty() && u.fragment.isNullOrEmpty()
    }

    /** 找同站点、同无痕状态的已有标签（含回过主页、页面被盖住的标签） */
    private fun findSiteTab(target: String, incognito: Boolean): Int {
        if (!looksLikeUrl(target)) return -1
        val key = siteKey(toUrl(target)) ?: return -1
        return tabs.indexOfFirst { tab ->
            tab.incognito == incognito && siteKey(tab.effectiveUrl) == key
        }
    }

    /**
     * 从主页 / 外部 / 应用内打开网址：
     * 1. 已有同站标签 → 直接切过去显示它原来的页面，不新建、不重新加载；
     *    仅当 [navigateIfDeepLink] 为 true 且目标是带具体路径的深链、且与该标签当前页不同，才在该标签里跳转。
     * 2. 没有同站标签 → 当前是空白/主页就地加载，否则新开标签。
     * 切走后遗留的空白标签会被清掉，避免多出一张空卡片。
     */
    fun openOrReuse(
        target: String,
        incognito: Boolean = activeTab.incognito,
        navigateIfDeepLink: Boolean = false,
        fromStartPage: Boolean = false,
    ) {
        if (target.isBlank()) return
        val idx = findSiteTab(target, incognito)
        if (idx >= 0) {
            val existing = tabs[idx]
            val blank = activeTab.takeIf { it.isBlank && it !== existing && tabs.size > 1 }
            existing.restoreFromHome()
            val url = toUrl(target)
            if (navigateIfDeepLink && !isSiteRoot(url) &&
                !existing.effectiveUrl.equals(url, ignoreCase = true)
            ) {
                existing.load(url)
            }
            if (blank != null) {
                tabs.remove(blank)
                blank.close()
            }
            activeIndex = tabs.indexOf(existing).coerceAtLeast(0)
            return
        }
        val cur = activeTab
        if (cur.incognito == incognito && cur.url.isEmpty() && (fromStartPage || cur.isBlank)) {
            cur.load(target)
        } else {
            newTab(target, incognito)
        }
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

    /** 后台打开，返回目标标签下标（同站已有则复用，失败返回 -1） */
    fun newTabInBackground(target: String): Int {
        // 同站已有标签：切换前不抢焦点，由调用方决定是否 select
        val idx = findSiteTab(target, false)
        if (idx >= 0) {
            val existing = tabs[idx]
            if (existing.url.isNotEmpty() && !isSiteRoot(toUrl(target)) &&
                !existing.url.equals(toUrl(target), ignoreCase = true)
            ) {
                existing.load(target)
            }
            return idx
        }
        if (tabs.size >= MAX_TABS) {
            android.widget.Toast.makeText(
                context,
                "最多同时打开 $MAX_TABS 个标签页",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            return -1
        }
        val tab = BrowserTab(context)
        tab.customUserAgent = userAgent
        tab.applyUserAgent()
        tabs.add(tab)
        tab.load(target)
        return tabs.lastIndex
    }

    /**
     * 清空某一栏的标签：[incognito] 为 true 清无痕栏，false 清普通栏，另一栏不受影响。
     * 当前标签被清掉时：清无痕栏且还有普通标签 → 回到最后一个普通标签；
     * 否则在被清空的那一栏新建一个空白标签。
     */
    fun closeAllOf(incognito: Boolean) {
        val removed = tabs.filter { it.incognito == incognito }
        if (removed.isEmpty()) return
        val keep = activeTab.takeIf { it.incognito != incognito }
        removed.forEach { it.close() }
        tabs.removeAll(removed)
        when {
            keep != null -> activeIndex = tabs.indexOf(keep).coerceAtLeast(0)
            incognito && tabs.isNotEmpty() -> activeIndex = tabs.lastIndex
            else -> newTab(incognito = incognito)
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


private const val MIN_FREE_BYTES = 64L * 1024 * 1024
private const val LARGE_BLOB_NOTICE_BYTES = 8L * 1024 * 1024

@Volatile
private var staleBlobFilesCleaned = false

/** 进程内第一次创建标签页时，清掉上次进程被杀 / 页面中途跳转遗留的 blob 缓存文件 */
private fun cleanStaleBlobFiles(context: Context) {
    if (staleBlobFilesCleaned) return
    staleBlobFilesCleaned = true
    runCatching {
        context.cacheDir.listFiles { f -> f.name.startsWith("mafari_blob_") }?.forEach { it.delete() }
    }
}
