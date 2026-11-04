package dev.dsh.miuixbrowser

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onApplyUserAgent: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenScripts: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenAbout: () -> Unit,
    onPageEnhancementsChange: (Boolean) -> Unit = {},
    blurEnabled: Boolean = true,
) {
    val context = LocalContext.current
    var showUa by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showWallpaper by remember { mutableStateOf(false) }
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
    val glass = rememberGlassActive(blurEnabled)
    val popupVisuals = GlassPopupDefaults.visuals()
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageGlassTopBar(
                title = "设置",
                backdrop = backdrop,
                glass = glass,
                scrollBehavior = scrollBehavior,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        val topInset = innerPadding.calculateTopPadding()
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .background(MiuixTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(top = topInset)
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
                        backdrop = if (glass) backdrop else null,
                        title = "显示模式",
                        items = listOf("跟随系统", "日间", "夜间"),
                        selectedIndex = settings.themeMode.coerceIn(0, 2),
                        onSelectedIndexChange = { settings.updateThemeMode(it) },
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
                        backdrop = if (glass) backdrop else null,
                        title = "地址栏显示",
                        items = listOf("网站标题", "主域名", "完整域名"),
                        selectedIndex = settings.addressDisplay.coerceIn(0, 2),
                        onSelectedIndexChange = { settings.updateAddressDisplay(it) },
                    )
                    GlassDropdownPreference(
                        backdrop = if (glass) backdrop else null,
                        title = "标签栏样式",
                        items = listOf("文字显示", "卡片显示"),
                        selectedIndex = settings.tabsStyle.coerceIn(0, 1),
                        onSelectedIndexChange = { settings.updateTabsStyle(it) },
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
                        backdrop = if (glass) backdrop else null,
                        title = "外部链接打开方式",
                        items = listOf("应用内打开", "自定义标签页", "其他浏览器"),
                        selectedIndex = settings.linkOpenMode.coerceIn(0, 2),
                        onSelectedIndexChange = { settings.updateLinkOpenMode(it) },
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
                        onClick = { showUa = true },
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
                    // Cookie 直接外部 dropdown，不再进 sheet
                    GlassDropdownPreference(
                        backdrop = if (glass) backdrop else null,
                        title = "Cookie 管理",
                        items = listOf("允许第三方 Cookie", "拒绝第三方 Cookie"),
                        selectedIndex = settings.thirdPartyCookie.coerceIn(0, 1),
                        onSelectedIndexChange = { settings.updateThirdPartyCookie(it) },
                    )
                    SettingsRow(
                        title = "网页权限",
                        summary = "摄像头 · 麦克风 · 定位 · 剪贴板 · 媒体发声",
                        onClick = { showPermSheet = true },
                    )
                    SwitchPreference(
                        title = "发送禁止跟踪请求",
                        summary = "地址栏与链接导航附带 DNT / Sec-GPC 头，并设置 navigator.doNotTrack",
                        checked = settings.sendDnt,
                        onCheckedChange = { settings.updateSendDnt(it) },
                    )
                    SwitchPreference(
                        title = "禁用 WebRTC",
                        summary = "移除 RTCPeerConnection 等接口，防止泄露真实 IP（刷新页面后生效）",
                        checked = settings.webrtcDisabled,
                        onCheckedChange = { settings.updateWebrtcDisabled(it) },
                    )
                    SwitchPreference(
                        title = "拦截跟踪脚本",
                        summary = "拦截第三方统计 / 追踪域名的请求（需开启页面增强）",
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
                        summary = "开关会同步启用 / 停用全部油猴脚本；关闭后也不注入去广告规则和长按标记",
                        checked = settings.pageEnhancements,
                        onCheckedChange = {
                            settings.updatePageEnhancements(it)
                            onPageEnhancementsChange(it)
                        },
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

            // UA 选择放在设置页自己的 Scaffold 里：浏览主界面的覆盖层会被二级页面盖住
            UaDialog(
                show = showUa,
                initial = settings.userAgent,
                // 不采样 browserBackdrop（主页可能含壁纸），用不透明回退色
                backdrop = null,
                onDismiss = { showUa = false },
                onConfirm = {
                    settings.updateUserAgent(it)
                    onApplyUserAgent(it)
                    showUa = false
                },
            )

            SearchSheet(
                show = showSearch,
                current = settings.searchIndex,
                custom = settings.customSearch,
                backdrop = if (glass) backdrop else null,
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
                backdrop = if (glass) backdrop else null,
                onDismissRequest = { showWallpaper = false },
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    WallpaperDialogRow(
                        title = "选择壁纸",
                        onClick = {
                            showWallpaper = false
                            picker.launch(arrayOf("image/*"))
                        },
                    )
                    WallpaperDialogRow(
                        title = "清除壁纸",
                        titleColor = MiuixTheme.colorScheme.error,
                        onClick = {
                            showWallpaper = false
                            settings.updateWallpaper("")
                        },
                    )
                    WallpaperDialogRow(
                        title = "模糊壁纸",
                        summary = "主页壁纸做高斯模糊，减少干扰",
                        onClick = { settings.updateWallpaperBlur(!settings.wallpaperBlur) },
                    ) {
                        Switch(
                            checked = settings.wallpaperBlur,
                            onCheckedChange = { settings.updateWallpaperBlur(it) },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    GlassDialogButton(
                        onClick = { showWallpaper = false },
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        minHeight = 44.dp,
                    ) {
                        Text(text = "关闭", fontSize = 16.sp, style = MiuixTheme.textStyles.button)
                    }
                }
            }

            GlassBottomSheet(
                show = showPermSheet,
                title = "网页权限",
                backdrop = if (glass) backdrop else null,
                onDismissRequest = { showPermSheet = false },
            ) {
                Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp)) {
                    val permOptions = listOf("询问", "允许", "拒绝")
                    listOf(
                        Triple("摄像头", settings.cameraPermission, settings::updateCameraPermission),
                        Triple("麦克风", settings.micPermission, settings::updateMicPermission),
                        Triple("定位", settings.locationPermission, settings::updateLocationPermission),
                        Triple("剪贴板读写", settings.clipboardPermission, settings::updateClipboardPermission),
                        Triple("网站媒体发声", settings.mediaAutoplay, settings::updateMediaAutoplay),
                    ).forEach { (label, current, update) ->
                        GlassDropdownPreference(
                            title = label,
                            items = permOptions,
                            selectedIndex = current.coerceIn(0, 2),
                            onSelectedIndexChange = { update(it) },
                            backdrop = if (glass) backdrop else null,
                            visuals = popupVisuals,
                            insideMargin = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 壁纸弹窗里的一行：标题统一 16sp、说明统一 12sp，右侧可放开关。 */
@Composable
private fun WallpaperDialogRow(
    title: String,
    onClick: () -> Unit,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    summary: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, color = titleColor)
            if (summary != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
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
