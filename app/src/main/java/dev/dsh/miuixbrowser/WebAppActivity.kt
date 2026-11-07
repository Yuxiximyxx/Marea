package dev.dsh.miuixbrowser

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * 「发送到桌面」生成的轻应用：只有一个全屏 WebView，没有地址栏、底栏、标签页。
 *
 * - 与 Mafari 浏览器共用同一份 WebView 数据（Cookie / 登录态 / 本地存储），也沿用代理、去广告网络拦截、深浅色设置。
 * - 同站跳转留在轻应用内；用户点击的跨站链接交给 Mafari 浏览器打开；登录 / 支付等无手势的跨站跳转原地放行。
 * - 返回键先回退网页历史，到头后退出；在最近任务里作为独立任务显示（标题 + 网站图标）。
 * - 不含油猴脚本、元素隐藏、标签页、下载页等浏览器功能；下载仍交给 Mafari 的下载器。
 */
class WebAppActivity : ComponentActivity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var settings: AppSettings
    private lateinit var insetsController: WindowInsetsControllerCompat

    private var startUrl = ""
    private var startHost = ""
    private var pageHost = ""
    private var appTitle = ""
    private var dark = false
    private var defaultBg = Color.WHITE

    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermission: ((Boolean) -> Unit)? = null

    private companion object {
        const val REQ_FILE = 7101
        const val REQ_PERM = 7102
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_FILE) return
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
        fileCallback = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERM) return
        val callback = pendingPermission
        pendingPermission = null
        callback?.invoke(grantResults.any { it == android.content.pm.PackageManager.PERMISSION_GRANTED })
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent?.data
        val scheme = data?.scheme?.lowercase()
        if (data == null || (scheme != "http" && scheme != "https")) {
            finish()
            return
        }
        startUrl = data.toString()
        startHost = data.host.orEmpty()
        pageHost = startHost
        appTitle = intent.getStringExtra(ShortcutHelper.EXTRA_TITLE).orEmpty().ifBlank { startHost }
        updateTaskDescription(appTitle)

        settings = AppSettings(this)
        AdBlock.init(this)
        WebViewProxy.applyFrom(settings)

        val systemDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        dark = when (settings.themeMode) {
            1 -> false
            2 -> true
            else -> systemDark
        }
        defaultBg = if (dark) 0xFF1A1A1A.toInt() else Color.WHITE

        // 全屏沉浸：页面画到状态栏 / 手势条下面，由根布局按系统栏留出边距
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        insetsController = WindowCompat.getInsetsController(window, window.decorView)
        applySystemBarIcons(defaultBg)

        root = FrameLayout(this).apply { setBackgroundColor(defaultBg) }
        webView = WebView(this).apply { setBackgroundColor(defaultBg) }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            isIndeterminate = false
        }
        root.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(
            progress,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.density * 2.5f).toInt()),
        )
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }

        configureWebView()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> hideCustomView()
                    webView.canGoBack() -> webView.goBack()
                    else -> finish()
                }
            }
        })

        if (savedInstanceState != null) webView.restoreState(savedInstanceState)
        if (webView.url == null) webView.loadUrl(startUrl)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
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
            setSupportMultipleWindows(false) // target=_blank / window.open 都在当前页内加载
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString.replace("; wv", "")
        }
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, true)
            }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, settings.thirdPartyCookie == 0)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress.visibility = View.GONE
                syncThemeColor()
                view.postDelayed({ syncThemeColor() }, 600)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                runCatching { Uri.parse(url).host }.getOrNull()?.takeIf { it.isNotBlank() }?.let { pageHost = it }
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                AdBlock.intercept(request, pageHost, settings.blockTrackingScripts)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return when (uri.scheme?.lowercase()) {
                    "http", "https" -> {
                        val host = uri.host.orEmpty()
                        val crossSite = host.isNotEmpty() && !isSameSite(host, startHost)
                        // 只有「用户亲手点的」跨站跳转才弹出到浏览器；登录 / 支付回调等自动跳转原地放行
                        if (crossSite && request.hasGesture() && !request.isRedirect && request.isForMainFrame) {
                            openInBrowser(uri)
                            true
                        } else {
                            false
                        }
                    }
                    "file", "about", "data", "javascript", "blob" -> false
                    "intent" -> {
                        launchIntentUri(uri.toString())
                        true
                    }
                    else -> {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        true
                    }
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                showErrorPage(view, request.url.toString(), error.description?.toString().orEmpty())
            }

            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                // WebView 渲染进程崩溃 / 被系统回收：返回 true 避免整个应用被带崩，直接退出，下次点图标重新打开
                finish()
                return true
            }

            override fun onReceivedHttpAuthRequest(
                view: WebView,
                handler: HttpAuthHandler,
                host: String?,
                realm: String?,
            ) {
                if (host != null && WebViewProxy.shouldAutoAuth(host)) {
                    handler.proceed(WebViewProxy.authUser, WebViewProxy.authPass)
                } else {
                    handler.cancel()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                if (!title.isNullOrBlank() && !title.startsWith("http")) updateTaskDescription(title)
            }

            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: WebChromeClient.FileChooserParams,
            ): Boolean {
                // 「另存为」类请求（showSaveFilePicker）轻应用不接，避免页面一直等待
                if (fileChooserParams.mode == 3) {
                    filePathCallback.onReceiveValue(null)
                    return true
                }
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return runCatching { startActivityForResult(fileChooserParams.createIntent(), REQ_FILE) }
                    .onFailure {
                        fileCallback = null
                        filePathCallback.onReceiveValue(null)
                    }
                    .let { true }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { handleWebPermission(request) }
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                runOnUiThread {
                    val host = runCatching { Uri.parse(origin).host }.getOrNull().orEmpty()
                    if (!isSameSite(host, startHost)) {
                        callback.invoke(origin, false, false)
                    } else {
                        ensurePermissions(
                            listOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                            anyOk = true,
                        ) { ok -> callback.invoke(origin, ok, false) }
                    }
                }
            }

            override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customCallback = callback
                view.setBackgroundColor(Color.BLACK)
                (window.decorView as ViewGroup).addView(
                    view,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }

            override fun onHideCustomView() = hideCustomView()
        }

        webView.setDownloadListener { link, userAgent, disposition, mime, _ ->
            if (link.startsWith("http", ignoreCase = true)) {
                val name = Downloader.withExtension(Downloader.fileNameFrom(link, disposition), mime)
                Downloader.start(this, link, userAgent, name, mime)
                Toast.makeText(this, "已开始下载，可在 Mafari「下载」中查看", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "这类下载请在 Mafari 浏览器中打开该网站再试", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ------------------------------------------------------------- helpers

    private fun hideCustomView() {
        val view = customView ?: return
        (window.decorView as ViewGroup).removeView(view)
        customView = null
        insetsController.show(WindowInsetsCompat.Type.systemBars())
        customCallback?.onCustomViewHidden()
        customCallback = null
    }

    private fun handleWebPermission(request: PermissionRequest) {
        val host = request.origin?.host.orEmpty()
        if (!isSameSite(host, startHost)) {
            request.deny()
            return
        }
        val needs = mutableListOf<String>()
        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in request.resources) needs += android.Manifest.permission.CAMERA
        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources) needs += android.Manifest.permission.RECORD_AUDIO
        val supported = setOf(
            PermissionRequest.RESOURCE_VIDEO_CAPTURE,
            PermissionRequest.RESOURCE_AUDIO_CAPTURE,
            PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
        )
        val grantable = request.resources.filter { it in supported }.toTypedArray()
        if (grantable.isEmpty()) {
            request.deny()
            return
        }
        ensurePermissions(needs, anyOk = false) { ok -> if (ok) request.grant(grantable) else request.deny() }
    }

    /** 缺哪个系统权限就申请哪个；[anyOk] 为 true 时任意一个通过即可（定位粗略 / 精确） */
    private fun ensurePermissions(permissions: List<String>, anyOk: Boolean, done: (Boolean) -> Unit) {
        fun granted(p: String) =
            checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val missing = permissions.filterNot { granted(it) }
        val already = if (anyOk) permissions.any { granted(it) } else missing.isEmpty()
        if (permissions.isEmpty() || already) {
            done(true)
            return
        }
        pendingPermission?.invoke(false)
        pendingPermission = { _ ->
            done(if (anyOk) permissions.any { granted(it) } else permissions.all { granted(it) })
        }
        requestPermissions(missing.toTypedArray(), REQ_PERM)
    }

    /** 用户亲手点的跨站链接：交给 Mafari 浏览器新开页面 */
    private fun openInBrowser(uri: Uri) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, uri)
                    .setClass(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        }
    }

    private fun launchIntentUri(raw: String) {
        runCatching {
            val parsed = Intent.parseUri(raw, Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
            }
            startActivity(parsed)
        }.onFailure {
            val fallback = runCatching {
                Intent.parseUri(raw, Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url")
            }.getOrNull()
            if (!fallback.isNullOrBlank()) {
                webView.loadUrl(fallback)
            } else {
                Toast.makeText(this, "未安装对应应用", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showErrorPage(view: WebView, failingUrl: String, reason: String) {
        val fg = if (dark) "#E6E6E6" else "#222222"
        val bg = if (dark) "#1A1A1A" else "#FFFFFF"
        val safeUrl = android.text.TextUtils.htmlEncode(failingUrl)
        val safeReason = android.text.TextUtils.htmlEncode(reason)
        val html = """
            <html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="margin:0;background:$bg;color:$fg;font-family:sans-serif;display:flex;
                flex-direction:column;align-items:center;justify-content:center;height:100vh;text-align:center">
              <div style="font-size:18px;margin-bottom:8px">页面加载失败</div>
              <div style="font-size:13px;opacity:.6;padding:0 24px;word-break:break-all">$safeReason</div>
              <a href="$safeUrl" style="margin-top:22px;padding:10px 28px;border-radius:22px;
                 background:#3B7DFF;color:#fff;text-decoration:none">重试</a>
            </body></html>
        """.trimIndent()
        view.loadDataWithBaseURL(failingUrl, html, "text/html", "UTF-8", failingUrl)
    }

    /** 状态栏 / 手势条区域的底色跟随网页（theme-color 或 body 背景），图标深浅随之切换 */
    private fun syncThemeColor() {
        val js = """
            (function(){
              function ok(s){return s&&s!=='transparent'&&!/rgba\(.*,\s*0\)${'$'}/.test(s)}
              var m=document.querySelector('meta[name="theme-color"]'),c=m&&m.content;
              if(c){var d=document.createElement('div');d.style.color=c;document.documentElement.appendChild(d);
                var r=getComputedStyle(d).color;d.remove();if(ok(r))return r}
              var b=document.body&&getComputedStyle(document.body).backgroundColor;if(ok(b))return b;
              var h=getComputedStyle(document.documentElement).backgroundColor;return ok(h)?h:''
            })()
        """.trimIndent()
        webView.evaluateJavascript(js) { value ->
            val color = parseCssColor(value?.trim('"').orEmpty()) ?: defaultBg
            root.setBackgroundColor(color)
            webView.setBackgroundColor(color)
            applySystemBarIcons(color)
        }
    }

    private fun parseCssColor(css: String): Int? {
        val m = Regex("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)").find(css) ?: return null
        val (r, g, b) = m.destructured
        return Color.rgb(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
    }

    private fun applySystemBarIcons(background: Int) {
        val light = ColorUtils.calculateLuminance(background) > 0.5
        insetsController.isAppearanceLightStatusBars = light
        insetsController.isAppearanceLightNavigationBars = light
    }

    @Suppress("DEPRECATION")
    private fun updateTaskDescription(title: String) {
        val icon = ShortcutHelper.readSavedIcon(this, intent?.getStringExtra(ShortcutHelper.EXTRA_ICON))
        setTaskDescription(ActivityManager.TaskDescription(title, icon))
    }
}
