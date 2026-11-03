package dev.dsh.miuixbrowser

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.webkit.WebViewCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onPickUa: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenScripts: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenAbout: () -> Unit,
    blurEnabled: Boolean = true,
) {
    val context = LocalContext.current
    var showSearch by remember { mutableStateOf(false) }
    var showWallpaper by remember { mutableStateOf(false) }
    var showCookieSheet by remember { mutableStateOf(false) }
    var showPermSheet by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            settings.updateWallpaper(uri.toString())
        }
    }

    val backdrop = rememberLayerBackdrop()
    val popupVisuals = GlassPopupDefaults.visuals()
    val scrollState = rememberScrollState()
    val scrollBehavior = MiuixScrollBehavior()
    val isScrolled by remember { derivedStateOf { scrollState.value > 0 } }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            GlassPageTopBar(
                title = "设置",
                backdrop = backdrop,
                onBack = onBack,
                progressiveBlur = blurEnabled,
                scrollBehavior = scrollBehavior,
                isContentScrolled = isScrolled,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(MiuixTheme.colorScheme.surface)
                .padding(top = padding.calculateTopPadding())
                .navigationBarsPadding()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp),
        ) {
            SmallTitle(text = "外观")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                GlassDropdownPreference(
                    title = "显示模式",
                    items = listOf("跟随系统", "日间", "夜间"),
                    selectedIndex = settings.themeMode.coerceIn(0, 2),
                    onSelectedIndexChange = { settings.updateThemeMode(it) },
                    backdrop = backdrop,
                    visuals = popupVisuals,
                )
                SettingsRow(
                    title = "主页壁纸",
                    summary = if (settings.wallpaper.isBlank()) "未设置" else "已设置",
                    onClick = { showWallpaper = true },
                )
                SwitchPreference(
                    title = "模糊效果",
                    summary = "关闭后底栏、收藏卡片和页面顶栏不再模糊",
                    checked = settings.blurEnabled,
                    onCheckedChange = { settings.updateBlur(it) },
                )
            }

            SmallTitle(text = "布局")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                GlassDropdownPreference(
                    title = "地址栏显示",
                    items = listOf("网站标题", "主域名", "完整域名"),
                    selectedIndex = settings.addressDisplay.coerceIn(0, 2),
                    onSelectedIndexChange = { settings.updateAddressDisplay(it) },
                    backdrop = backdrop,
                    visuals = popupVisuals,
                )
                GlassDropdownPreference(
                    title = "标签栏样式",
                    items = listOf("文字显示", "卡片显示"),
                    selectedIndex = settings.tabsStyle.coerceIn(0, 1),
                    onSelectedIndexChange = { settings.updateTabsStyle(it) },
                    backdrop = backdrop,
                    visuals = popupVisuals,
                )
            }

            SmallTitle(text = "浏览")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                GlassDropdownPreference(
                    title = "外部链接打开方式",
                    items = listOf("应用内打开", "自定义标签页", "其他浏览器"),
                    selectedIndex = settings.linkOpenMode.coerceIn(0, 2),
                    onSelectedIndexChange = { settings.updateLinkOpenMode(it) },
                    backdrop = backdrop,
                    visuals = popupVisuals,
                )
                SettingsRow(
                    title = "搜索引擎",
                    summary = SEARCH_ENGINES.getOrNull(settings.searchIndex)?.name ?: "自定义",
                    onClick = { showSearch = true },
                )
                SettingsRow(
                    title = "书签与历史",
                    summary = "书签分组、浏览记录",
                    onClick = onOpenBookmarks,
                )
                SettingsRow(
                    title = "UA 标识管理",
                    summary = uaSummary(settings.userAgent),
                    onClick = onPickUa,
                )
                SettingsRow(
                    title = "下载",
                    summary = "查看已下载的文件",
                    onClick = onOpenDownloads,
                )
            }

            SmallTitle(text = "隐私与安全")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                SettingsRow(
                    title = "Cookie 管理",
                    summary = if (settings.thirdPartyCookie == 0) "允许第三方 Cookie" else "拒绝第三方 Cookie",
                    onClick = { showCookieSheet = true },
                )
                SettingsRow(
                    title = "网页权限",
                    summary = "摄像头 · 麦克风 · 定位",
                    onClick = { showPermSheet = true },
                )
                SwitchPreference(
                    title = "发送禁止跟踪请求",
                    summary = "页面请求附带 DNT / Sec-GPC 头",
                    checked = settings.sendDnt,
                    onCheckedChange = { settings.updateSendDnt(it) },
                )
                SwitchPreference(
                    title = "禁用 WebRTC",
                    summary = "防止通过 WebRTC 泄露真实 IP",
                    checked = settings.webrtcDisabled,
                    onCheckedChange = { settings.updateWebrtcDisabled(it) },
                )
                SwitchPreference(
                    title = "拦截跟踪脚本",
                    summary = "结合页面增强，尽量阻断追踪器",
                    checked = settings.blockTrackingScripts,
                    onCheckedChange = { settings.updateBlockTrackingScripts(it) },
                )
            }

            SmallTitle(text = "高级")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                SettingsRow(
                    title = "脚本管理",
                    summary = "用户脚本 / 油猴脚本",
                    onClick = onOpenScripts,
                )
                SwitchPreference(
                    title = "页面增强",
                    summary = "关闭后不注入油猴脚本、去广告规则和长按标记",
                    checked = settings.pageEnhancements,
                    onCheckedChange = { settings.updatePageEnhancements(it) },
                )
            }

            SmallTitle(text = "关于")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                SettingsRow(
                    title = "关于 Mafari",
                    summary = "版本 " + BuildConfig.VERSION_NAME,
                    onClick = onOpenAbout,
                )
            }
        }

        SearchSheet(
            show = showSearch,
            current = settings.searchIndex,
            custom = settings.customSearch,
            backdrop = backdrop,
            onDismiss = { showSearch = false },
            onPick = {
                settings.updateSearch(it)
                showSearch = false
            },
            onCustom = {
                settings.updateCustomSearch(it)
                showSearch = false
            },
        )

        GlassDialog(
            show = showWallpaper,
            title = "主页壁纸",
            backdrop = backdrop,
            onDismissRequest = { showWallpaper = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "选择壁纸",
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showWallpaper = false
                            picker.launch(arrayOf("image/*"))
                        }
                        .padding(horizontal = 6.dp, vertical = 12.dp),
                )
                Text(
                    text = "清除壁纸",
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showWallpaper = false
                            settings.updateWallpaper("")
                        }
                        .padding(horizontal = 6.dp, vertical = 12.dp),
                )
                Spacer(Modifier.height(8.dp))
                GlassDialogButton(
                    onClick = { showWallpaper = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "关闭", style = MiuixTheme.textStyles.button)
                }
            }
        }

        GlassBottomSheet(
            show = showCookieSheet,
            title = "Cookie 管理",
            backdrop = backdrop,
            onDismissRequest = { showCookieSheet = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp)) {
                listOf(
                    "允许第三方 Cookie" to 0,
                    "拒绝第三方 Cookie" to 1,
                ).forEach { (label, mode) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                settings.updateThirdPartyCookie(mode)
                                showCookieSheet = false
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            fontSize = 16.sp,
                            color = if (settings.thirdPartyCookie == mode) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (settings.thirdPartyCookie == mode) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer { rotationZ = 180f },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "拒绝后部分站点登录态可能失效，可在系统设置中为单站点例外。",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        GlassBottomSheet(
            show = showPermSheet,
            title = "网页权限",
            backdrop = backdrop,
            onDismissRequest = { showPermSheet = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp)) {
                listOf(
                    Triple("摄像头", settings.cameraPermission, settings::updateCameraPermission),
                    Triple("麦克风", settings.micPermission, settings::updateMicPermission),
                    Triple("定位", settings.locationPermission, settings::updateLocationPermission),
                ).forEach { (label, current, update) ->
                    SmallTitle(text = label)
                    listOf("询问" to 0, "允许" to 1, "拒绝" to 2).forEach { (opt, mode) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    update(mode)
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = opt,
                                fontSize = 15.sp,
                                color = if (current == mode) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (current == mode) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .graphicsLayer { rotationZ = 180f },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, summary: String, onClick: () -> Unit) {
    ArrowPreference(
        title = title,
        summary = summary,
        onClick = onClick,
    )
}

@Composable
internal fun webViewVersion(): String? {
    val context = LocalContext.current
    return remember(context) {
        runCatching { WebViewCompat.getCurrentWebViewPackage(context)?.versionName }.getOrNull()
    }
}

private const val PROJECT_URL = "https://github.com/rosenii/mafari"
private const val RELEASE_API = "https://api.github.com/repos/rosenii/mafari/releases/latest"

@Composable
private fun CheckUpdateButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(
            text = if (checking) "检查中…" else "检查更新",
            onClick = {
                if (checking) return@TextButton
                checking = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { fetchLatestVersion() }
                    checking = false
                    if (result == null) {
                        Toast.makeText(context, "检查失败，请稍后再试", Toast.LENGTH_SHORT).show()
                    } else {
                        val current = BuildConfig.VERSION_NAME.removePrefix("v")
                        val latest = result.first.removePrefix("v")
                        if (latest == current) {
                            Toast.makeText(context, "已是最新版本 $current", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "发现新版本 $latest", Toast.LENGTH_LONG).show()
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(result.second)))
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun fetchLatestVersion(): Pair<String, String>? = runCatching {
    val connection = (java.net.URL(RELEASE_API).openConnection() as java.net.HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 8000
        setRequestProperty("User-Agent", "mafari")
        setRequestProperty("Accept", "application/vnd.github+json")
    }
    try {
        if (connection.responseCode !in 200..299) return@runCatching null
        val text = connection.inputStream.bufferedReader().readText()
        val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
            ?: return@runCatching null
        val url = Regex("\"html_url\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
            ?: PROJECT_URL + "/releases"
        tag to url
    } finally {
        connection.disconnect()
    }
}.getOrNull()

private fun uaSummary(ua: String): String = when {
    ua.isBlank() -> "跟随系统"
    ua == DESKTOP_UA -> "桌面 Chrome"
    else -> ua.take(48) + if (ua.length > 48) "…" else ""
}

@Composable
private fun SearchSheet(
    show: Boolean,
    current: Int,
    custom: String,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
    onCustom: (String) -> Unit,
) {
    var text by remember(show) { mutableStateOf(custom) }
    GlassBottomSheet(
        show = show,
        title = "搜索引擎",
        backdrop = backdrop,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp)) {
            SEARCH_ENGINES.forEachIndexed { index, engine ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(index) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon = rememberSearchIcon(engine.iconUrl)
                    if (icon != null) {
                        Image(
                            bitmap = icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = engine.name,
                        fontSize = 16.sp,
                        color = if (index == current) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = "自定义（用 %s 代表关键词）",
                singleLine = true,
            )
            Spacer(Modifier.height(14.dp))
            TextButton(
                text = "保存自定义",
                onClick = { onCustom(text) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
