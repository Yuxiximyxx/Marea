package dev.dsh.miuixbrowser

import android.app.Activity
import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState

import androidx.compose.foundation.layout.widthIn
import top.yukonga.miuix.kmp.icon.extended.Layers
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassDefaults
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassMaterials
import top.yukonga.miuix.kmp.glass.GlassShape
import top.yukonga.miuix.kmp.glass.GlassStrokes
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class Sheet { None, Tabs, Menu, Link, Image, Element }

private data class DownloadRequest(
    val url: String,
    val userAgent: String?,
    val fileName: String,
    val mime: String?,
)

private fun androidx.compose.ui.geometry.Offset.round(): IntOffset =
    IntOffset(x.toInt(), y.toInt())

@Composable
fun BrowserScreen(
    settings: AppSettings,
    initialUrl: String? = null,
    onInitialUrlConsumed: () -> Unit = {},
    openDownloads: Boolean = false,
    onOpenDownloadsConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val state = remember { BrowserState(context) }
    val favorites = remember { FavoritesStore(context) }
    remember { Diag.log("BrowserScreen composed, favorites=" + favorites.items.size) }
    val menuOrder = remember { MenuOrderStore(context) }
    val wallpaperBitmap = rememberWallpaper(settings.wallpaper)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val wallpaperBackdrop = rememberLayerBackdrop()
    val browserSurfaceColor = MiuixTheme.colorScheme.surface
    val browserBackdrop = rememberLayerBackdrop {
        // 给采集层补一层不透明底色，避免网页透明区域出现模糊采样伪影。
        drawRect(browserSurfaceColor)
        drawContent()
    }

    // 从其他应用点进来的链接：新开标签页
    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            state.newTab(initialUrl)
            onInitialUrlConsumed()
        }
    }
    val extensions = remember { ExtensionStore(context) }
    val history = remember { HistoryStore(context) }
    val tab = state.activeTab
    // 页面加载期间也禁用 MIUIX 采集，避免 URL 尚未更新时误把 WebView 帧送进 RuntimeShader。
    val homeBlurAvailable = tab.url.isEmpty() && !tab.loading
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current

    var sheet by remember { mutableStateOf(Sheet.None) }
    var linkUrl by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var elementHit by remember { mutableStateOf<BrowserTab.ElementHit?>(null) }
    var address by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var barHidden by remember { mutableStateOf(false) }
    /** 底栏被左右滑动缩小为圆形搜索钮 */
    var barMinimized by remember { mutableStateOf(false) }
    var favEditing by remember { mutableStateOf(false) }
    var showFavEditor by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showUaPicker by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showExtensions by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var libraryTab by remember { mutableIntStateOf(1) }
    var pendingScript by remember { mutableStateOf<String?>(null) }
    var settingsReturn by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<DownloadRequest?>(null) }
    var showFind by remember { mutableStateOf(false) }
    var editingFavIndex by remember { mutableStateOf(-1) }
    // 离场动画期间保留最近一次打开的弹层内容
    var lastActiveSheet by remember { mutableStateOf(Sheet.None) }

    LaunchedEffect(openDownloads) {
        if (openDownloads) {
            showDownloads = true
            sheet = Sheet.None
            onOpenDownloadsConsumed()
        }
    }

    LaunchedEffect(tab.id, extensions.items.size, tab.id) {
        tab.userScripts = extensions.items.toList()
        tab.onVisited = { url, title -> history.record(url, title) }
    }

    LaunchedEffect(tab.id, settings.searchTemplate) {
        tab.searchTemplate = settings.searchTemplate
        tab.onPullRefresh = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            tab.webView.reload()
        }
        tab.onUserScriptLink = { url -> pendingScript = url }
        tab.onDownload = { url, ua, disposition, mime ->
            pendingDownload = DownloadRequest(
                url = url,
                userAgent = ua,
                fileName = Downloader.fileNameFrom(url, disposition),
                mime = mime,
            )
        }
    }

    LaunchedEffect(tab.id) {
        tab.onLongPressLink = { link ->
            linkUrl = link
            sheet = Sheet.Link
        }
        tab.onLongPressImage = { url ->
            imageUrl = url
            sheet = Sheet.Image
        }
        tab.onElementLongPress = { hit ->
            elementHit = hit
            sheet = Sheet.Element
        }
        editing = false
        barHidden = false
        barMinimized = false
    }
    // URL 发生变化时先清掉上一页标题，避免新页面加载期间地址栏继续显示旧标题。
    // WebChromeClient.onReceivedTitle 更新 tab.title 后，下面的同步逻辑会立即显示新标题。
    var addressTitleUrl by remember(tab.id) { mutableStateOf(tab.url) }
    LaunchedEffect(tab.id, tab.url) {
        if (addressTitleUrl != tab.url) {
            addressTitleUrl = tab.url
            // 真正导航加载时旧标题会残留；同文档 history.pushState 则保留当前标题。
            tab.title = if (tab.loading) "" else tab.webView.title.orEmpty()
        }
    }

    LaunchedEffect(tab.id, tab.url, editing, settings.addressDisplay, tab.title) {
        if (!editing) {
            address = when (settings.addressDisplay) {
                0 -> if (tab.url.isBlank()) "" else tab.title.ifBlank { tab.url }
                1 -> if (tab.url.isBlank()) "" else hostOf(tab.url)
                else -> tab.url
            }
        }
    }

    // 回到主页时把 WebView 隐藏掉，否则网页会从主页底下透出来
    LaunchedEffect(tab.id, tab.url) {
        tab.webView.alpha = if (tab.url.isEmpty()) 0f else 1f
    }
    DisposableEffect(tab.id) { onDispose { tab.onLongPressLink = null } }

    // WebView 不会把滚动事件抛给 Compose，这里轻量轮询页面滚动位置：
    // 向下滚超过一小段 → 收起底栏；向上滚或回到顶部 → 展开。
    LaunchedEffect(tab.id, extensions.items.size, tab.id) {
        tab.userScripts = extensions.items.toList()
        tab.onVisited = { url, title -> history.record(url, title) }
    }

    LaunchedEffect(tab.id, settings.searchTemplate) {
        tab.searchTemplate = settings.searchTemplate
        tab.onPullRefresh = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            tab.webView.reload()
        }
        tab.onUserScriptLink = { url -> pendingScript = url }
        tab.onDownload = { url, ua, disposition, mime ->
            pendingDownload = DownloadRequest(
                url = url,
                userAgent = ua,
                fileName = Downloader.fileNameFrom(url, disposition),
                mime = mime,
            )
        }
    }

    LaunchedEffect(tab.id) {
        var last = 0
        var lastShow = 0
        var scrollDownAccum = 0f
        var scrollUpAccum = 0f
        while (true) {
            runCatching {
                val webView = tab.webView
                val offset = webView.scrollY.coerceAtLeast(0)
                val dy = offset - last
                when {
                    offset <= 12 -> {
                        barHidden = false
                        lastShow = offset
                        scrollDownAccum = 0f
                        scrollUpAccum = 0f
                    }
                    dy > 0 -> {
                        scrollUpAccum = 0f
                        scrollDownAccum += dy
                        // 向下累计约 100px 且离开顶部足够远才隐藏，降低误触
                        if (scrollDownAccum >= 100f && offset - lastShow >= 96f) {
                            barHidden = true
                            scrollDownAccum = 0f
                            scrollUpAccum = 0f
                        }
                    }
                    dy < 0 -> {
                        scrollDownAccum = 0f
                        // 隐藏后须向上累计约 120px 才恢复，避免轻微回弹就弹出
                        scrollUpAccum += -dy
                        if (barHidden && scrollUpAccum >= 120f) {
                            barHidden = false
                            lastShow = offset
                            scrollUpAccum = 0f
                        } else if (!barHidden) {
                            lastShow = offset
                        }
                    }
                }
                last = offset
            }
            delay(80)
        }
    }

    val statusBarHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val animatedTopPadding by animateDpAsState(
        targetValue = if (barHidden) 0.dp else statusBarHeight,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "statusInset",
    )
    // 弹簧会过冲到负值，padding 不允许负数
    val contentTopPadding = animatedTopPadding.coerceAtLeast(0.dp)

    val hide by animateFloatAsState(
        targetValue = if (barHidden && sheet == Sheet.None) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "bottomBar",
    )

    BackHandler {
        when {
            // 子页面：先回设置页（关于 / 脚本 / 下载一致）
            showAbout -> {
                showAbout = false
                if (settingsReturn) {
                    showSettings = true
                    settingsReturn = false
                }
            }
            showExtensions && settingsReturn -> {
                showExtensions = false
                showSettings = true
                settingsReturn = false
            }
            showDownloads && settingsReturn -> {
                showDownloads = false
                showSettings = true
                settingsReturn = false
            }
            showExtensions -> showExtensions = false
            showHistory -> {
                showHistory = false
                if (settingsReturn) {
                    showSettings = true
                    settingsReturn = false
                }
            }
            showSettings -> showSettings = false
            showDownloads -> showDownloads = false
            showUaPicker -> showUaPicker = false
            showFind -> {
                tab.clearFind()
                showFind = false
            }
            showFavEditor -> showFavEditor = false
            sheet != Sheet.None -> sheet = Sheet.None
            tab.canGoBack -> tab.goBack()
            state.tabs.size > 1 -> state.closeTab(state.activeIndex)
            else -> (context as? Activity)?.finish()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
    Box(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        // 采集网页 / 首页内容，供外部的 MIUIX 玻璃底栏取样。底栏本身不能放进采集层。
        Box(
            modifier = Modifier
                .fillMaxSize()
                // MIUIX backdrop 仅在主页采集 Compose 内容；网页状态不采集 WebView，
                // 避免 AndroidView / SurfaceView 与 RuntimeShader 组合时出现闪烁或崩溃。
                .then(if (homeBlurAvailable) Modifier.layerBackdrop(browserBackdrop) else Modifier),
        ) {

        // ---- 首页壁纸：铺满全屏，盖住状态栏 ----
        if (tab.url.isEmpty() && wallpaperBitmap != null) {
            val wallpaperImage = wallpaperBitmap
            if (wallpaperImage != null) {
                Box(modifier = Modifier.fillMaxSize().layerBackdrop(wallpaperBackdrop)) {
                    Image(
                        bitmap = wallpaperImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
                }
            }
        }

        // ---- 网页内容：从状态栏下面开始，不再压到状态栏 ----
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentTopPadding),
            ) {
                key(tab.id) {
                    AndroidView(
                        factory = { _ ->
                            // AndroidView.factory 接收的是 Context，而不是 View。
                            // 这里返回已经由 BrowserTab 持有的 WebView 实例。
                            tab.webView.apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                androidx.core.view.ViewCompat.requestApplyInsets(this)
                                requestLayout()
                            }
                        },
                        update = { view ->
                            // update 在 View 已进入 AndroidView 容器后执行；此时重新请求 Insets
                            // 才能确保 WebView 获得当前 Window 的真实可用区域。
                            view.post {
                                if (view.isAttachedToWindow) {
                                    androidx.core.view.ViewCompat.requestApplyInsets(view)
                                    view.requestLayout()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { size ->
                                // Compose 的尺寸才是当前沉浸式内容区域的真实可用尺寸。
                                // 尺寸有效后让 WebView 重新布局，再通知页面重新计算响应式布局。
                                if (size.width > 0 && size.height > 0) {
                                    tab.webView.post {
                                        if (tab.webView.isAttachedToWindow) {
                                            tab.webView.requestLayout()
                                            tab.webView.invalidate()
                                            tab.webView.evaluateJavascript(
                                                """
                                                (function() {
                                                    try {
                                                        window.dispatchEvent(new Event('resize'));
                                                        if (window.visualViewport) {
                                                            window.visualViewport.dispatchEvent(new Event('resize'));
                                                        }
                                                    } catch (_) {}
                                                })();
                                                """.trimIndent(),
                                                null
                                            )
                                        }
                                    }
                                }
                            },
                    )
                }
                if (tab.url.isEmpty()) {
                    if (wallpaperBitmap == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MiuixTheme.colorScheme.surface),
                        )
                    }
                    StartPage(
                        wallpaper = wallpaperBitmap,
                        wallpaperBackdrop = wallpaperBackdrop,
                        blurEnabled = settings.blurEnabled,
                        favorites = favorites.items,
                        editing = favEditing,
                        onOpen = { tab.load(it) },
                        onToggleEdit = { favEditing = !favEditing },
                        onRemove = { favorites.remove(it) },
                        onEdit = { index ->
                            editingFavIndex = index
                            showFavEditor = true
                        },
                        onAdd = {
                            editingFavIndex = -1
                            showFavEditor = true
                        },
                    )
                }
            }
            if (tab.url.isNotEmpty()) {
                PullRefreshArea(
                    canPull = { tab.webView.scrollY <= 0 },
                    onRefresh = { tab.webView.reload() },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            if (tab.pullProgress >= 0.99f) {
                RefreshSpinner(
                    progress = 1f,
                    refreshing = false,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp),
                )
            }
            if (tab.loading) {
                ProgressLine(
                    progress = tab.progress,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = contentTopPadding),
                )
            }
        }
        } // backdrop capture layer ends before the floating toolbar

        // ---- 底部：地址栏 + 工具栏 ----
        // 根据地址文本长度动态调整宽度，并使用弹簧动画平滑过渡。
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp),
        ) {
            // 按实际字体宽度计算地址栏所需空间：固定按钮区 + 地址文字 + 地址栏内边距。
            // 不设置人为最小宽度，短文字不会留下空白；只有屏幕可用宽度作为上限。
            val textMeasurer = rememberTextMeasurer()
            val addressMeasureText = address.ifBlank { "搜索或输入网址" }
            val measuredAddressPx = textMeasurer.measure(
                text = AnnotatedString(addressMeasureText),
                style = TextStyle(fontSize = 15.sp),
                maxLines = 1,
            ).size.width
            val measuredAddressWidth = with(LocalDensity.current) { measuredAddressPx.toDp() }
            val fixedToolbarWidth = 44.dp * 4 + 12.dp + 24.dp
            val loadingIndicatorWidth = if (tab.loading) 15.dp else 0.dp
            val requestedBarWidth = fixedToolbarWidth + measuredAddressWidth + loadingIndicatorWidth
            // 显式比较 Dp，避免当前 Kotlin/Compose 版本对 coerceAtMost 的错误重载推断。
            val targetBarWidth = if (requestedBarWidth < maxWidth) requestedBarWidth else maxWidth
            val animatedBarWidth by animateDpAsState(
                targetValue = targetBarWidth,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                label = "dynamicBottomBarWidth",
            )
            var dragDistance by remember(tab.id) { mutableFloatStateOf(0f) }
            var hDragDistance by remember(tab.id) { mutableFloatStateOf(0f) }

            // 退出动画播放期间仍保留底栏；动画结束后直接从组合树移除，
            // 不留下透明/平移后的 Compose 节点拦截 WebView 的触摸事件。
            if (!barHidden || hide < 0.995f) {
                // 缩小后的圆形搜索钮
                if (barMinimized) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                            .size(48.dp)
                            .pointerInput(tab.id) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        hDragDistance += dragAmount.x
                                        // 再次左右滑或上滑可展开
                                        if (kotlin.math.abs(hDragDistance) > 72f || dragAmount.y < -40f) {
                                            barMinimized = false
                                            hDragDistance = 0f
                                            change.consume()
                                        }
                                    },
                                    onDragEnd = { hDragDistance = 0f },
                                    onDragCancel = { hDragDistance = 0f },
                                )
                            }
                            .graphicsLayer {
                                translationY = hide * 210.dp.toPx()
                                alpha = 1f - hide
                            }
                            .clickable {
                                barMinimized = false
                                if (!editing) {
                                    editing = true
                                    address = tab.url
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .glassBar(
                                    backdrop = browserBackdrop,
                                    corner = 24.dp,
                                    blurEnabled = settings.blurEnabled,
                                    textureBlurEnabled = homeBlurAvailable,
                                ),
                        )
                        Icon(
                            imageVector = MiuixIcons.Search,
                            contentDescription = "搜索或输入网址",
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(animatedBarWidth)
                        // 在底栏上向下滑动主动收起；左右滑动缩小为搜索圆钮。
                        .pointerInput(tab.id, barHidden) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y)) {
                                        hDragDistance += dragAmount.x
                                        if (kotlin.math.abs(hDragDistance) > 72f) {
                                            barMinimized = true
                                            hDragDistance = 0f
                                            change.consume()
                                        }
                                    } else {
                                        dragDistance += dragAmount.y
                                        // 提高竖向隐藏阈值，降低误触
                                        if (dragDistance > 56f && !barHidden) {
                                            barHidden = true
                                            change.consume()
                                        } else if (dragDistance < -56f && barHidden) {
                                            barHidden = false
                                            change.consume()
                                        }
                                    }
                                },
                                onDragEnd = {
                                    dragDistance = 0f
                                    hDragDistance = 0f
                                },
                                onDragCancel = {
                                    dragDistance = 0f
                                    hDragDistance = 0f
                                },
                            )
                        }
                        .graphicsLayer {
                            translationY = hide * 210.dp.toPx()
                            alpha = 1f - hide
                            val s = 1f - 0.08f * hide
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
            BottomBar(
                blurEnabled = settings.blurEnabled,
                textureBlurEnabled = homeBlurAvailable,
                backdrop = browserBackdrop,
                address = address,
                loading = tab.loading,
                canGoBack = tab.canGoBack,
                canGoForward = tab.canGoForward,
                tabCount = state.tabs.size,
                onChange = {
                    address = it
                    editing = true
                },
                onGo = {
                    if (address.isNotBlank()) {
                        editing = false
                        focus.clearFocus()
                        tab.load(address)
                    }
                },
                onBack = { tab.goBack() },
                onForward = { tab.goForward() },
                onHome = { tab.goHome() },
                onCopyAddress = {
                    if (tab.url.isNotBlank()) clipboard.setText(AnnotatedString(tab.url))
                },
                onAddressTap = {
                    if (!editing) {
                        editing = true
                        address = tab.url
                    }
                },
                editing = editing,
                onShare = {
                    if (tab.url.isNotBlank()) {
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, tab.url)
                                    },
                                    "分享链接",
                                ),
                            )
                        }
                    }
                },
                onTabs = { sheet = Sheet.Tabs },
                onMore = { sheet = Sheet.Menu },
            )
                }
                } // end else (!barMinimized)
            }
        }

        // 隐藏状态不放置透明热区：底部网页区域完全交给 WebView 处理点击、长按和滚动。
        // 通过 WebView 的 scrollY 方向判断下拉恢复，不通过点击底部区域恢复。

        // ---- 弹层：GlassBottomSheet（保持组合直到离场动画结束）----
        val activeSheet = sheet
        if (activeSheet != Sheet.None) {
            lastActiveSheet = activeSheet
        }
        GlassBottomSheet(
            show = activeSheet != Sheet.None,
            title = when (lastActiveSheet) {
                Sheet.Link -> hostOf(linkUrl)
                Sheet.Image -> "图片"
                Sheet.Element -> "页面元素"
                else -> null
            },
            backdrop = if (homeBlurAvailable && settings.blurEnabled) browserBackdrop else null,
            onDismissRequest = { sheet = Sheet.None },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            ) {
            when (lastActiveSheet) {
            Sheet.Tabs -> {
                if (settings.tabsStyle == 1) {
                    // 卡片显示
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val rows = state.tabs.chunked(2)
                        rows.forEachIndexed { rowIndex, rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowItems.forEachIndexed { colIndex, item ->
                                    val index = rowIndex * 2 + colIndex
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(84.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                                            .clickable {
                                                state.select(index)
                                                sheet = Sheet.None
                                            }
                                            .padding(10.dp),
                                    ) {
                                        Text(
                                            text = item.title.ifBlank { "新标签页" },
                                            fontSize = 13.sp,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = if (item.url.isBlank()) "主页" else hostOf(item.url),
                                                fontSize = 11.sp,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            IconButton(
                                                onClick = { state.closeTab(index) },
                                                minWidth = 28.dp,
                                                minHeight = 28.dp,
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.Close,
                                                    contentDescription = "关闭",
                                                    modifier = Modifier.size(14.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                                repeat(2 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                } else {
                    state.tabs.forEachIndexed { index, item ->
                        SheetRow(
                            title = item.title.ifBlank { "新标签页" },
                            subtitle = item.url.ifBlank { null },
                            trailing = {
                                BarButton(
                                    icon = MiuixIcons.Close,
                                    description = "关闭",
                                    enabled = state.tabs.size > 1,
                                    onClick = { state.closeTab(index) },
                                )
                            },
                            onClick = {
                                state.select(index)
                                sheet = Sheet.None
                            },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    text = "新建标签页",
                    onClick = {
                        state.newTab()
                        sheet = Sheet.None
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
            }

            Sheet.Menu -> {
                MenuGrid(
                    labelFor = { action ->
                        if (action == MenuAction.Desktop) {
                            if (tab.desktopMode) "移动版" else "桌面版"
                        } else {
                            action.label
                        }
                    },
                    order = menuOrder.order,
                    onAction = { action ->
                        sheet = Sheet.None
                        when (action) {
                            MenuAction.NewTab -> state.newTab()
                            MenuAction.Desktop -> tab.toggleDesktopMode(!tab.desktopMode)
                            MenuAction.Find -> showFind = true
                            MenuAction.Translate -> if (tab.url.isNotBlank()) {
                                state.newTab(
                                    "https://translate.google.com/translate?sl=auto&tl=zh-CN&u=" +
                                        java.net.URLEncoder.encode(tab.url, "UTF-8"),
                                )
                            }
                            MenuAction.Favorite -> if (tab.url.isNotBlank()) {
                                favorites.add(tab.title.ifBlank { hostOf(tab.url) }, tab.url)
                            }
                            MenuAction.Share -> if (tab.url.isNotBlank()) {
                                runCatching {
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, tab.url)
                                            },
                                            "分享链接",
                                        ),
                                    )
                                }
                            }
                            MenuAction.Refresh -> tab.reload()
                            MenuAction.CopyLink -> if (tab.url.isNotBlank()) {
                                clipboard.setText(AnnotatedString(tab.url))
                            }
                            MenuAction.External -> if (tab.url.isNotBlank()) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tab.url)))
                                }
                            }
                            MenuAction.Home -> tab.goHome()
                            MenuAction.Downloads -> showDownloads = true
                            MenuAction.Extensions -> showExtensions = true
                            MenuAction.History -> {
                                libraryTab = 1
                                showHistory = true
                            }
                            MenuAction.Settings -> showSettings = true
                            MenuAction.CloseTab -> state.closeTab(state.activeIndex)
                        }
                    },
                    onMove = { from, to -> menuOrder.move(from, to) },
                )
            }

            Sheet.Link -> {
                SheetRow(title = "打开链接") {
                    sheet = Sheet.None
                    tab.loadUrl(linkUrl)
                }
                SheetRow(title = "在新标签页打开") {
                    sheet = Sheet.None
                    state.newTabInBackground(linkUrl)
                    state.select(state.tabs.lastIndex)
                }
                SheetRow(title = "复制链接") {
                    sheet = Sheet.None
                    clipboard.setText(AnnotatedString(linkUrl))
                }
                SheetRow(title = "在外部浏览器打开") {
                    sheet = Sheet.None
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl))) }
                }
            }

            Sheet.Image -> {
                val inlineImage = imageUrl.startsWith("data:", ignoreCase = true)
                SheetRow(
                    title = "保存图片",
                    subtitle = if (inlineImage) "内嵌图片" else hostOf(imageUrl),
                ) {
                    sheet = Sheet.None
                    if (inlineImage) {
                        val name = "image_" + System.currentTimeMillis() + ".png"
                        val ok = Downloader.saveDataUri(context, imageUrl, name)
                        android.widget.Toast.makeText(
                            context,
                            if (ok) "已保存到 Download/mafari" else "保存失败",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        pendingDownload = DownloadRequest(
                            url = imageUrl,
                            userAgent = null,
                            fileName = Downloader.fileNameFrom(imageUrl, null),
                            mime = null,
                        )
                    }
                }
                if (!inlineImage) {
                    SheetRow(title = "复制图片地址") {
                        sheet = Sheet.None
                        clipboard.setText(AnnotatedString(imageUrl))
                    }
                    SheetRow(title = "在新标签页打开") {
                        sheet = Sheet.None
                        state.newTabInBackground(imageUrl)
                        state.select(state.tabs.lastIndex)
                    }
                }
            }

            Sheet.Element -> {
                val hit = elementHit
                if (hit != null) {
                    if (hit.imageUrl.isNotBlank()) {
                        SheetRow(title = "保存图片", subtitle = hostOf(hit.imageUrl)) {
                            sheet = Sheet.None
                            pendingDownload = DownloadRequest(
                                url = hit.imageUrl,
                                userAgent = null,
                                fileName = Downloader.fileNameFrom(hit.imageUrl, null),
                                mime = null,
                            )
                        }
                    }
                    SheetRow(
                        title = "标记为广告并移除",
                        subtitle = "记住这条规则，下次访问该站点自动隐藏",
                    ) {
                        sheet = Sheet.None
                        UserAdRules.add(context, hostOf(tab.url), hit.selector)
                        tab.webView.evaluateJavascript(removeElementJs(hit.selector), null)
                    }
                    SheetRow(title = "仅移除该元素") {
                        sheet = Sheet.None
                        tab.webView.evaluateJavascript(removeElementJs(hit.selector), null)
                    }
                }
            }

            Sheet.None -> Unit
            }
            }
        }

        if (showSettings) {
            SettingsScreen(
                settings = settings,
                onBack = { showSettings = false },
                onPickUa = { showUaPicker = true },
                onOpenDownloads = {
                    settingsReturn = true
                    showSettings = false
                    showDownloads = true
                },
                onOpenScripts = {
                    settingsReturn = true
                    showSettings = false
                    showExtensions = true
                },
                onOpenBookmarks = {
                    settingsReturn = true
                    showSettings = false
                    libraryTab = 0
                    showHistory = true
                },
                onOpenHistory = {
                    settingsReturn = true
                    showSettings = false
                    libraryTab = 1
                    showHistory = true
                },
                onOpenAbout = {
                    settingsReturn = true
                    showSettings = false
                    showAbout = true
                },
                blurEnabled = settings.blurEnabled,
            )
        }


        if (showAbout) {
            AboutScreen(
                onBack = {
                    showAbout = false
                    if (settingsReturn) {
                        showSettings = true
                        settingsReturn = false
                    }
                },
                blurEnabled = settings.blurEnabled,
            )
        }

        val downloadRequest = pendingDownload
        GlassDialog(
            show = downloadRequest != null,
            title = "下载文件",
            summary = downloadRequest?.let { it.fileName + "\n保存到 /sdcard/Download/mafari" },
            backdrop = if (homeBlurAvailable) browserBackdrop else null,
            onDismissRequest = { pendingDownload = null },
        ) {
            if (downloadRequest != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GlassDialogButton(
                            onClick = { pendingDownload = null },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = "取消", style = MiuixTheme.textStyles.button)
                        }
                        GlassDialogButton(
                            onClick = {
                                pendingDownload = null
                                scope.launch {
                                    Downloader.start(
                                        context = context,
                                        url = downloadRequest.url,
                                        userAgent = downloadRequest.userAgent,
                                        fileName = downloadRequest.fileName,
                                        mimeType = downloadRequest.mime,
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            role = GlassDialogButtonRole.Primary,
                        ) {
                            Text(text = "下载", style = MiuixTheme.textStyles.button)
                        }
                    }
                }
            }
        }

        val scriptImportUrl = pendingScript
        GlassDialog(
            show = scriptImportUrl != null,
            title = "导入脚本？",
            summary = scriptImportUrl,
            backdrop = if (homeBlurAvailable) browserBackdrop else null,
            onDismissRequest = { pendingScript = null },
        ) {
            if (scriptImportUrl != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GlassDialogButton(
                            onClick = { pendingScript = null },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = "取消", style = MiuixTheme.textStyles.button)
                        }
                        GlassDialogButton(
                            onClick = {
                                val target = scriptImportUrl
                                pendingScript = null
                                scope.launch {
                                    val code = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        fetchRemoteScript(target)
                                    }
                                    if (code != null) {
                                        extensions.add("", code, sourceUrl = target)
                                        showExtensions = true
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            role = GlassDialogButtonRole.Primary,
                        ) {
                            Text(text = "导入", style = MiuixTheme.textStyles.button)
                        }
                    }
                }
            }
        }

        if (showDownloads) {
            DownloadsScreen(onBack = { showDownloads = false }, blurEnabled = settings.blurEnabled)
        }

        if (showExtensions) {
            ExtensionsScreen(
                store = extensions,
                onBack = { showExtensions = false },
                blurEnabled = settings.blurEnabled,
            )
        }

        if (showHistory) {
            LibraryScreen(
                favorites = favorites,
                store = history,
                onBack = {
                    showHistory = false
                    if (settingsReturn) {
                        showSettings = true
                        settingsReturn = false
                    }
                },
                onOpen = { url ->
                    showHistory = false
                    settingsReturn = false
                    tab.loadUrl(url)
                },
                initialTab = libraryTab,
                blurEnabled = settings.blurEnabled,
            )
        }

        UaDialog(
            show = showUaPicker,
            initial = settings.userAgent,
            backdrop = if (homeBlurAvailable) browserBackdrop else null,
            onDismiss = { showUaPicker = false },
            onConfirm = {
                settings.updateUserAgent(it)
                state.applyUserAgent(it)
                showUaPicker = false
            },
        )

        FindDialog(
            show = showFind,
            backdrop = if (homeBlurAvailable) browserBackdrop else null,
            onDismiss = {
                tab.clearFind()
                showFind = false
            },
            onQuery = { tab.findAll(it) },
            onPrev = { tab.findNext(false) },
            onNext = { tab.findNext(true) },
        )

        FavoriteEditor(
            show = showFavEditor,
            initial = favorites.items.getOrNull(editingFavIndex),
            backdrop = if (homeBlurAvailable) browserBackdrop else null,
            onDismiss = { showFavEditor = false },
            onConfirm = { name, url ->
                if (editingFavIndex >= 0) {
                    favorites.update(editingFavIndex, name, url)
                } else {
                    favorites.add(name, url)
                }
                showFavEditor = false
            },
        )
    }
    }
}

/* ------------------------------ 玻璃 ------------------------------ */

/** MIUIX Glass 底板：主页用 glassPanel 真玻璃；无采样/关模糊时回退到渐变底板。 */
@Composable
private fun Modifier.glassBar(
    backdrop: LayerBackdrop?,
    corner: Dp,
    blurEnabled: Boolean = true,
    textureBlurEnabled: Boolean = true,
): Modifier {
    val dark = isAppDarkTheme()
    val glassShape = GlassShape(corner)
    val rounded = RoundedCornerShape(corner)
    val canUseGlass = blurEnabled && textureBlurEnabled && backdrop != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    // 关闭模糊时使用接近不透明的纯色底板；无采样时用渐变近似玻璃。
    val tint = when {
        !blurEnabled -> Brush.verticalGradient(
            listOf(
                MiuixTheme.colorScheme.surface.copy(alpha = 0.97f),
                MiuixTheme.colorScheme.surface.copy(alpha = 0.97f),
            ),
        )
        dark -> Brush.verticalGradient(listOf(Color(0xC82B2B31), Color(0xB51A1A20)))
        else -> Brush.verticalGradient(listOf(Color(0xC8FFFFFF), Color(0xBDE7EAF0)))
    }

    return this.glassPanel(
        backdrop = if (canUseGlass) backdrop else null,
        shape = glassShape,
        style = GlassDefaults.style(dark),
        material = if (canUseGlass) GlassMaterials.puredThinGlass(dark) else null,
        stroke = if (blurEnabled) GlassStrokes.forTheme(dark) else null,
        shading = false,
        fallback = Modifier
            .clip(rounded)
            .background(tint, rounded)
            .border(
                width = 1.dp,
                color = if (dark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.9f),
                shape = rounded,
            ),
    )
}

@Composable
private fun BottomBar(
    blurEnabled: Boolean = true,
    textureBlurEnabled: Boolean = true,
    backdrop: LayerBackdrop,
    address: String,
    loading: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onHome: () -> Unit,
    tabCount: Int,
    onChange: (String) -> Unit,
    onGo: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onShare: () -> Unit,
    onTabs: () -> Unit,
    onMore: () -> Unit,
    onCopyAddress: () -> Unit,
    onAddressTap: () -> Unit = {},
    editing: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(29.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
    ) {
        // 空玻璃底板与按钮内容分层，避免 backdrop 采集层递归包含自身。
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassBar(
                    backdrop = backdrop,
                    corner = 29.dp,
                    blurEnabled = blurEnabled,
                    textureBlurEnabled = textureBlurEnabled,
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                // 不裁剪内容层：按钮的柔光投影需要向按钮边缘外自然扩散。
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PressIcon(icon = MiuixIcons.Back, description = "后退", enabled = canGoBack, onClick = onBack)
            if (canGoForward) {
                PressIcon(
                    icon = MiuixIcons.Back,
                    description = "前进",
                    enabled = true,
                    flip = true,
                    onClick = onForward,
                )
            } else {
                PressIcon(
                    icon = MiuixIcons.Home,
                    description = "主页",
                    enabled = true,
                    onClick = onHome,
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onAddressTap() },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCopyAddress()
                        },
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (loading) {
                    LoadingDot()
                    Spacer(Modifier.width(8.dp))
                }
                AddressField(
                    value = address,
                    onChange = onChange,
                    onGo = onGo,
                    editing = editing,
                    onStartEdit = onAddressTap,
                    modifier = Modifier.weight(1f),
                )
            }

            TabIcon(count = tabCount, onClick = onTabs, onLongClick = onMore)
            PressIcon(icon = MiuixIcons.More, description = "更多", onClick = onMore)
        }
    }
}

/**
 * 图标轮廓重叠柔影：在原始矢量图标下方叠放两层轻微错位、模糊的同形图标。
 * 阴影跟随实际图标路径，不绘制圆形底影或按钮外轮廓。
 */
@Composable
private fun IconWithContourShadow(
    imageVector: ImageVector,
    description: String,
    tint: Color,
    scale: Float,
    flip: Boolean = false,
) {
    val shadowAlpha = if (tint.alpha < 0.5f) 0.12f else 0.28f
    Box(contentAlignment = Alignment.Center) {
        // 暗色投影：向右下轻微错位，模糊后沿图标边缘自然扩散。
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = Color.Black.copy(alpha = shadowAlpha),
            modifier = Modifier
                .size(24.dp)
                .offset(x = 0.8.dp, y = 1.0.dp)
                .blur(2.2.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    if (flip) rotationZ = 180f
                },
        )
        // 反向浅色柔影与暗影交叠，形成玻璃质感的轮廓层次。
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (tint.alpha < 0.5f) 0.10f else 0.24f),
            modifier = Modifier
                .size(24.dp)
                .offset(x = (-0.6).dp, y = (-0.6).dp)
                .blur(1.6.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    if (flip) rotationZ = 180f
                },
        )
        // 清晰图标置顶，避免阴影影响图标辨识度。
        Icon(
            imageVector = imageVector,
            contentDescription = description,
            tint = tint,
            modifier = Modifier
                .size(21.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    if (flip) rotationZ = 180f
                },
        )
    }
}

@Composable
private fun TabIcon(count: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "tabPress",
    )
    val color = MiuixTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            IconWithContourShadow(
                imageVector = MiuixIcons.Layers,
                description = "标签页",
                tint = color,
                scale = scale,
            )
            if (count > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .height(13.dp)
                        .widthIn(min = 13.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MiuixTheme.colorScheme.primary)
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = count.toString(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun PressIcon(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    flip: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val color = if (enabled) {
        MiuixTheme.colorScheme.onSurface
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        IconWithContourShadow(
            imageVector = icon,
            description = description,
            tint = color,
            scale = scale,
            flip = flip,
        )
    }
}

/** 加载时地址栏里的小转点 */
@Composable
private fun LoadingDot() {
    val transition = rememberInfiniteTransition(label = "load")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot",
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.primary.copy(alpha = alpha)),
    )
}

/* ------------------------------ 小部件 ------------------------------ */

@Composable
private fun AddressField(
    value: String,
    onChange: (String) -> Unit,
    onGo: () -> Unit,
    editing: Boolean = true,
    onStartEdit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val color = MiuixTheme.colorScheme.onSurface
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) {
            Text(
                text = "搜索或输入网址",
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = color),
            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!editing) {
            // 未编辑时盖一层透明点击区，点一下立刻切到完整网址
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onStartEdit,
                    ),
            )
        }
    }
}

@Composable
private fun BarButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    flip: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (enabled) {
        MiuixTheme.colorScheme.onSurface
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = color,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { if (flip) rotationZ = 180f },
        )
    }
}

@Composable
private fun TabCountButton(count: Int, onClick: () -> Unit) {
    val color = MiuixTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.6.dp, color, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = count.toString(), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

@Composable
private fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.5.dp)
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                .height(2.5.dp)
                .background(MiuixTheme.colorScheme.primary),
        )
    }
}

/* ------------------------------ 弹层行 ------------------------------ */

@Composable
private fun SheetRow(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** 按选择器移除页面元素（长按菜单用） */
private fun removeElementJs(selector: String): String =
    "(function(){try{var el=document.querySelector(" + org.json.JSONObject.quote(selector) +
        ");if(el){el.style.setProperty('display','none','important');el.remove();}}catch(e){}})();"

/* ------------------------------ 主页 ------------------------------ */

@Composable
private fun StartPage(
    wallpaper: ImageBitmap?,
    wallpaperBackdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    favorites: List<Favorite>,
    editing: Boolean,
    onOpen: (String) -> Unit,
    onToggleEdit: () -> Unit,
    onRemove: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 44.dp, bottom = 150.dp),
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassLogoText("Mafari", fontSize = 58)
        }
        Spacer(Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "收藏",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            if (editing) {
                TextButton(
                    text = "完成",
                    onClick = onToggleEdit,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    minHeight = 28.dp,
                    minWidth = 52.dp,
                    cornerRadius = 14.dp,
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    textStyle = TextStyle(fontSize = 12.sp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            favorites.chunked(3).forEachIndexed { rowIndex, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowItems.forEachIndexed { colIndex, favorite ->
                        val index = rowIndex * 3 + colIndex
                        FavoriteTile(
                            favorite = favorite,
                            wallpaper = wallpaper,
                            wallpaperBackdrop = wallpaperBackdrop,
                            blurEnabled = blurEnabled,
                            editing = editing,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (editing) onEdit(index) else onOpen(favorite.url)
                            },
                            onLongPress = {
                                if (!editing) onToggleEdit()
                            },
                            onRemove = { onRemove(index) },
                        )
                    }
                    repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    if (editing && rowIndex == favorites.chunked(3).lastIndex && rowItems.size == 3) {
                        // 满行时最后单独加一行
                    }
                }
            }
            if (editing) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = 1.dp,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(16.dp),
                            )
                            .clickable(onClick = onAdd),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "+ 添加收藏",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.weight(2f))
                }
            }
        }
        }
    }
}

@Composable
private fun FavoriteTile(
    favorite: Favorite,
    wallpaper: ImageBitmap?,
    wallpaperBackdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    editing: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val useBlur = blurEnabled && wallpaper != null && wallpaperBackdrop != null
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(shape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPress()
                },
            )
            .then(
                if (useBlur) {
                    Modifier.textureBlurBar(
                        backdrop = wallpaperBackdrop,
                        shape = shape,
                        tint = Color.White.copy(alpha = 0.28f),
                        radius = 22f,
                        highlight = Highlight.GlassStrokeMiddleLight,
                    )
                } else {
                    Modifier
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                        .border(
                            width = 1.dp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            shape = shape,
                        )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val icon = rememberFavicon(favorite.url)
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.25f)),
                )
            }
            Text(
                text = favorite.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (editing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "×", fontSize = 11.sp, color = Color.White)
            }
        }
    }
}

/** 新增 / 编辑收藏：GlassDialog */
@Composable
private fun FavoriteEditor(
    show: Boolean,
    initial: Favorite?,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var url by remember(initial) { mutableStateOf(initial?.url ?: "") }
    GlassDialog(
        show = show,
        title = if (initial == null) "添加收藏" else "编辑收藏",
        backdrop = backdrop,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = "名称",
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = "网址",
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassDialogButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(text = "取消", style = MiuixTheme.textStyles.button)
                }
                GlassDialogButton(
                    onClick = { if (url.isNotBlank()) onConfirm(name, url) },
                    modifier = Modifier.weight(1f),
                    role = GlassDialogButtonRole.Primary,
                ) {
                    Text(text = "保存", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}

/** UA 选择：GlassBottomSheet 预设 + 自定义 */
@Composable
private fun UaDialog(
    show: Boolean,
    initial: String,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var custom by remember(show) { mutableStateOf(initial) }
    GlassBottomSheet(
        show = show,
        title = "自定义 UA",
        backdrop = backdrop,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp)) {
            UA_PRESETS.forEach { preset ->
                Text(
                    text = preset.name,
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onConfirm(preset.value) }
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            TextField(
                value = custom,
                onValueChange = { custom = it },
                modifier = Modifier.fillMaxWidth(),
                label = "UA 字符串",
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassDialogButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(text = "取消", style = MiuixTheme.textStyles.button)
                }
                GlassDialogButton(
                    onClick = { onConfirm(custom) },
                    modifier = Modifier.weight(1f),
                    role = GlassDialogButtonRole.Primary,
                ) {
                    Text(text = "保存", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}

/** 页内查找：GlassDialog */
@Composable
private fun FindDialog(
    show: Boolean,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    onDismiss: () -> Unit,
    onQuery: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    GlassDialog(
        show = show,
        title = "查找页内",
        backdrop = backdrop,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = text,
                onValueChange = {
                    text = it
                    onQuery(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = "查找内容",
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassDialogButton(onClick = onPrev, modifier = Modifier.weight(1f)) {
                    Text(text = "上一个", style = MiuixTheme.textStyles.button)
                }
                GlassDialogButton(onClick = onNext, modifier = Modifier.weight(1f)) {
                    Text(text = "下一个", style = MiuixTheme.textStyles.button)
                }
                GlassDialogButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    role = GlassDialogButtonRole.Primary,
                ) {
                    Text(text = "关闭", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}


/** 更多面板：图标 + 文字宫格，长按拖动排序 */
@Composable
private fun MenuGrid(
    labelFor: (MenuAction) -> String,
    order: List<MenuAction>,
    onAction: (MenuAction) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val density = LocalDensity.current
    val cellHeight = 88.dp
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val columns = 4

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cellWidth = maxWidth / columns
        val cellWidthPx = with(density) { cellWidth.toPx() }
        val cellHeightPx = with(density) { cellHeight.toPx() }

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            order.chunked(columns).forEachIndexed { row, rowItems ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEachIndexed { col, action ->
                        val index = row * columns + col
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(cellHeight)
                                .graphicsLayer {
                                    if (index == dragIndex) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                        scaleX = 1.08f
                                        scaleY = 1.08f
                                    }
                                    alpha = if (dragIndex >= 0 && index != dragIndex) 0.6f else 1f
                                }
                                .pointerInput(action, order.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragIndex = index
                                            dragOffset = Offset.Zero
                                        },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            dragOffset += delta
                                        },
                                        onDragEnd = {
                                            val dCol = (dragOffset.x / cellWidthPx).roundToInt()
                                            val dRow = (dragOffset.y / cellHeightPx).roundToInt()
                                            val target = (row + dRow) * columns + (col + dCol)
                                            if (target != index) {
                                                onMove(index, target.coerceIn(0, order.lastIndex))
                                            }
                                            dragIndex = -1
                                            dragOffset = Offset.Zero
                                        },
                                        onDragCancel = {
                                            dragIndex = -1
                                            dragOffset = Offset.Zero
                                        },
                                    )
                                }
                                .clickable { onAction(action) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = labelFor(action),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(26.dp),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = labelFor(action),
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private val GoogleColors = listOf(
    Color(0xFF4285F4),
    Color(0xFFEA4335),
    Color(0xFFFBBC05),
    Color(0xFF4285F4),
    Color(0xFF34A853),
    Color(0xFFEA4335),
)

/** 顶部下拉刷新：只响应页面已在顶部时的向下拖动 */
@Composable
private fun PullRefreshArea(
    canPull: () -> Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var drag by remember { mutableFloatStateOf(0f) }
    val threshold = 140f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onVerticalDrag = { change, dy ->
                        if (canPull() && dy > 0 && drag < threshold * 1.6f) {
                            drag += dy
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        if (drag > threshold) onRefresh()
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (drag > 12f) {
            Box(
                modifier = Modifier
                    .size((24 + (drag / 6f)).coerceIn(24f, 40f).dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (drag > threshold) "松手刷新" else "下拉",
                    fontSize = 9.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun GlassLogoText(text: String, fontSize: Int) {
    val brush = Brush.verticalGradient(
        listOf(
            Color(0xFFF7FAFF),
            Color(0xFFB9C8DB),
            Color(0xFF6F8299),
            Color(0xFFD9E3EE),
        ),
    )
    val shadow = Shadow(color = Color(0x66000000), offset = Offset(0f, 3f), blurRadius = 8f)
    Box(contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            text.forEach { ch ->
                Text(
                    text = ch.toString(),
                    style = TextStyle(
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.45f),
                    ),
                    modifier = Modifier.graphicsLayer {
                        renderEffect = BlurEffect(14f, 14f, TileMode.Clamp)
                    },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            text.forEach { ch ->
                Text(
                    text = ch.toString(),
                    style = TextStyle(
                        brush = brush,
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Black,
                        shadow = shadow,
                    ),
                )
            }
        }
    }
}

/** 抓取远程脚本 */
private fun fetchRemoteScript(url: String): String? = runCatching {
    val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 8000
        setRequestProperty("User-Agent", "Mozilla/5.0 mafari")
    }
    try {
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.bufferedReader().readText()
    } finally {
        connection.disconnect()
    }
}.getOrNull()

/** 顶部下拉刷新指示器 */
@Composable
private fun RefreshSpinner(progress: Float, refreshing: Boolean, modifier: Modifier = Modifier) {
    val rotation = if (refreshing) {
        val transition = rememberInfiniteTransition(label = "refresh")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
            label = "angle",
        )
        angle
    } else {
        progress * 360f
    }
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MiuixIcons.Refresh,
            contentDescription = "刷新",
            tint = MiuixTheme.colorScheme.primary.copy(alpha = if (refreshing) 1f else 0.4f + 0.6f * progress),
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

/** 从 content:// 读壁纸 */
@Composable
private fun rememberWallpaper(uri: String): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = if (uri.isBlank()) {
            null
        } else {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                        android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    return image
}
