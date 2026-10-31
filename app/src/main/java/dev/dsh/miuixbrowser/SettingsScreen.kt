package dev.dsh.miuixbrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalContext
import androidx.webkit.WebViewCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.height
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onPickUa: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenScripts: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    var showTheme by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showAddress by remember { mutableStateOf(false) }
    var showLinkMode by remember { mutableStateOf(false) }
    var showTabsStyle by remember { mutableStateOf(false) }
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

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "设置",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SmallTitle(text = "外观")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    SettingsRow(
                        title = "显示模式",
                        summary = when (settings.themeMode) {
                            1 -> "日间"
                            2 -> "夜间"
                            else -> "跟随系统"
                        },
                        onClick = { showTheme = true },
                    )
                    SettingsRow(
                        title = "主页壁纸",
                        summary = if (settings.wallpaper.isBlank()) "未设置" else "已设置（点按更换）",
                        onClick = { picker.launch(arrayOf("image/*")) },
                    )
                    if (settings.wallpaper.isNotBlank()) {
                        TextButton(
                            text = "清除壁纸",
                            onClick = { settings.updateWallpaper("") },
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("柔光玻璃", fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "底栏与卡片使用高斯模糊",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Switch(checked = settings.blurEnabled, onCheckedChange = { settings.updateBlur(it) })
                    }
                }

                SmallTitle(text = "布局")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    SettingsRow(
                        title = "地址栏显示",
                        summary = when (settings.addressDisplay) {
                            0 -> "网站标题"
                            1 -> "主域名"
                            else -> "完整域名"
                        },
                        onClick = { showAddress = true },
                    )
                    SettingsRow(
                        title = "标签栏样式",
                        summary = if (settings.tabsStyle == 1) "卡片显示" else "文字显示",
                        onClick = { showTabsStyle = true },
                    )
                }

                SmallTitle(text = "浏览")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    SettingsRow(
                        title = "搜索引擎",
                        summary = SEARCH_ENGINES.getOrNull(settings.searchIndex)?.name ?: "自定义",
                        onClick = { showSearch = true },
                    )
                    SettingsRow(
                        title = "自定义 UA",
                        summary = uaSummary(settings.userAgent),
                        onClick = onPickUa,
                    )
                    SettingsRow(
                        title = "外部链接打开方式",
                        summary = when (settings.linkOpenMode) {
                            1 -> "自定义标签页 (Custom Tabs)"
                            2 -> "其他浏览器"
                            else -> "应用内打开"
                        },
                        onClick = { showLinkMode = true },
                    )
                    SettingsRow(
                        title = "下载",
                        summary = "查看已下载的文件",
                        onClick = onOpenDownloads,
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
    }

    if (showLinkMode) {
        ChoiceDialog(
            title = "外部链接打开方式",
            options = listOf("应用内打开", "自定义标签页 (Custom Tabs)", "其他浏览器"),
            current = settings.linkOpenMode,
            onDismiss = { showLinkMode = false },
        ) { settings.updateLinkOpenMode(it); showLinkMode = false }
    }

    if (showAddress) {
        ChoiceDialog(
            title = "地址栏显示",
            options = listOf("网站标题", "主域名", "完整域名"),
            current = settings.addressDisplay,
            onDismiss = { showAddress = false },
        ) { settings.updateAddressDisplay(it); showAddress = false }
    }

    if (showTabsStyle) {
        ChoiceDialog(
            title = "标签栏样式",
            options = listOf("文字显示", "卡片显示"),
            current = settings.tabsStyle,
            onDismiss = { showTabsStyle = false },
        ) { settings.updateTabsStyle(it); showTabsStyle = false }
    }

    if (showSearch) {
        SearchDialog(
            current = settings.searchIndex,
            custom = settings.customSearch,
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
    }

    if (showTheme) {
        ThemeDialog(
            current = settings.themeMode,
            onDismiss = { showTheme = false },
            onPick = {
                settings.updateThemeMode(it)
                showTheme = false
            },
        )
    }
}

@Composable
private fun SettingsRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurface)
            Spacer(Modifier.height(3.dp))
            Text(summary, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1)
        }
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
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
private fun ThemeDialog(current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    OverlayDialog(show = true, title = "显示模式", onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            listOf("跟随系统", "日间", "夜间").forEachIndexed { index, label ->
                Text(
                    text = label + if (index == current) "  ✓" else "",
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(index) }
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchDialog(
    current: Int,
    custom: String,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
    onCustom: (String) -> Unit,
) {
    var text by remember { mutableStateOf(custom) }
    OverlayDialog(show = true, title = "搜索引擎", onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SEARCH_ENGINES.forEachIndexed { index, engine ->
                Text(
                    text = engine.name + if (index == current) "  ✓" else "",
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(index) }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = "自定义（用 %s 代表关键词）",
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(
                text = "保存自定义",
                onClick = { onCustom(text) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    current: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    OverlayDialog(show = true, title = title, onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                Text(
                    text = label + if (index == current) "  ✓" else "",
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(index) }
                        .padding(vertical = 11.dp, horizontal = 6.dp),
                )
            }
        }
    }
}
