package dev.dsh.miuixbrowser

import android.content.Intent
import android.net.Uri
import android.webkit.WebSettings
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDefaults
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassShadows
import top.yukonga.miuix.kmp.glass.GlassShape
import top.yukonga.miuix.kmp.glass.GlassStrokes
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit = {},
    blurEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kernel = webViewVersion() ?: "未知"
    val ua = remember { runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault("未知") }
    var checkingUpdate by remember { mutableStateOf(false) }

    val pageBackdrop = rememberLayerBackdrop()
    val glass = rememberGlassActive(blurEnabled)
    val scrollBehavior = MiuixScrollBehavior()
    val dark = isAppDarkTheme()
    val surface = MiuixTheme.colorScheme.surface
    val iconShape = RoundedCornerShape(26.dp)
    var pendingUpdate by remember { mutableStateOf<AppRelease?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageGlassTopBar(
                title = "关于",
                backdrop = pageBackdrop,
                glass = glass,
                scrollBehavior = scrollBehavior,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        val topInset = innerPadding.calculateTopPadding()
        Box(modifier = Modifier.fillMaxSize().background(surface)) {
            // ---- 整页背景：铺满全屏的主题色渐变，供玻璃图标与顶栏采样（顶栏在录制层之外）----
            Box(modifier = Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
                Box(modifier = Modifier.fillMaxSize().background(surface))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to MiuixTheme.colorScheme.primary.copy(alpha = 0.38f),
                                0.45f to Color.Transparent,
                                1f to MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f),
                            ),
                        ),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(top = topInset)
                    .padding(bottom = 24.dp),
            ) {
                // ---- 头部：图标下移避开顶栏标题 + 版本号下检查更新 ----
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .then(
                                if (glass) {
                                    Modifier.glassPanel(
                                        backdrop = pageBackdrop,
                                        shape = GlassShape(26.dp),
                                        style = GlassDefaults.Style,
                                        stroke = GlassStrokes.forTheme(dark, GlassStrokes.MiddleLight, GlassStrokes.MiddleDark),
                                        shadow = GlassShadows.Regular,
                                        shading = true,
                                    )
                                } else {
                                    Modifier.background(MiuixTheme.colorScheme.surfaceContainerHigh, iconShape)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface),
                            modifier = Modifier.size(58.dp),
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Text(
                        text = "Mafari",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "版本 " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )

                    Spacer(Modifier.height(16.dp))

                    GlassDialogButton(
                        onClick = {
                            if (checkingUpdate) return@GlassDialogButton
                            checkingUpdate = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { fetchLatestRelease() }
                                checkingUpdate = false
                                if (result == null) {
                                    Toast.makeText(context, "检查失败，请稍后再试", Toast.LENGTH_SHORT).show()
                                } else {
                                    val current = BuildConfig.VERSION_NAME.removePrefix("v")
                                    val latest = result.version.removePrefix("v")
                                    if (!isNewerVersion(result.version, BuildConfig.VERSION_NAME)) {
                                        Toast.makeText(context, "已是最新版本 $current", Toast.LENGTH_SHORT).show()
                                    } else if (result.apkUrl.isNullOrBlank()) {
                                        // 无 APK 资产时在应用内浏览页打开发布页
                                        Toast.makeText(context, "发现新版本 $latest", Toast.LENGTH_LONG).show()
                                        onOpenUrl(result.pageUrl)
                                    } else {
                                        pendingUpdate = result
                                    }
                                }
                            }
                        },
                        role = GlassDialogButtonRole.Primary,
                        modifier = Modifier.width(200.dp).liquidButton(14.dp),
                    ) {
                        Text(
                            text = if (checkingUpdate) "检查中…" else "检查更新",
                            style = MiuixTheme.textStyles.button,
                        )
                    }
                }

                SmallTitle(text = "版本")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    AboutRow("MIUIX Glass", "0.9.4 · miuix-glass")
                    AboutRow("内核版本", kernel)
                }

                SmallTitle(text = "本机 UA")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Text(
                        text = ua,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                SmallTitle(text = "项目")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidButton(14.dp)
                            .clickable { onOpenUrl(PROJECT_URL) }
                            .padding(16.dp),
                    ) {
                        Text("项目地址", fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "https://github.com/rosenii/mafari",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Mafari · 基于 MIUIX Glass 与系统 WebView",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            // 应用内下载更新（不再跳浏览器）
            val update = pendingUpdate
            top.yukonga.miuix.kmp.glass.GlassDialog(
                show = update != null,
                title = "发现新版本",
                summary = update?.let { "${it.version} · 下载后可在下载页安装" },
                backdrop = if (glass) pageBackdrop else null,
                onDismissRequest = { pendingUpdate = null },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    top.yukonga.miuix.kmp.glass.GlassDialogButton(
                        onClick = { pendingUpdate = null },
                        modifier = Modifier.weight(1f).liquidButton(14.dp),
                    ) {
                        Text(text = "取消", style = MiuixTheme.textStyles.button)
                    }
                    top.yukonga.miuix.kmp.glass.GlassDialogButton(
                        onClick = {
                            val rel = update ?: return@GlassDialogButton
                            val apk = rel.apkUrl
                            if (apk != null) {
                                Toast.makeText(context, "正在选择下载线路…", Toast.LENGTH_SHORT).show()
                                Thread {
                                    val url = GitHubAccelerate.pickDownloadUrl(apk)
                                    android.os.Handler(context.mainLooper).post {
                                        Downloader.start(
                                            context = context,
                                            url = url,
                                            userAgent = null,
                                            fileName = "mafari-${rel.version.removePrefix("v")}.apk",
                                            mimeType = "application/vnd.android.package-archive",
                                        )
                                        Toast.makeText(context, "已开始下载，完成后可在下载页安装", Toast.LENGTH_LONG).show()
                                    }
                                }.start()
                            }
                            pendingUpdate = null
                        },
                        modifier = Modifier.weight(1f).liquidButton(14.dp),
                        role = top.yukonga.miuix.kmp.glass.GlassDialogButtonRole.Primary,
                    ) {
                        Text(text = "下载", style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }
    }
}

data class AppRelease(
    val version: String,
    val pageUrl: String,
    val apkUrl: String?,
    val notes: String = "",
)

private const val PROJECT_URL = "https://github.com/rosenii/mafari"
private const val RELEASE_API = "https://api.github.com/repos/rosenii/mafari/releases/latest"

/**
 * 拉取最新 release：版本号、页面地址、APK 直链。
 * 直连 + 公共 GitHub 加速镜像并发探测，连通者优先。
 */
internal fun fetchLatestRelease(): AppRelease? {
    val fetchers: List<() -> AppRelease?> = GitHubAccelerate.MirrorPrefixes.map { prefix ->
        {
            val api = GitHubAccelerate.map(RELEASE_API, prefix)
            fetchReleaseFrom(api)
        }
    }
    return GitHubAccelerate.raceFirst(8000, fetchers)
}

private fun fetchReleaseFrom(apiUrl: String): AppRelease? = runCatching {
    val connection = (java.net.URL(apiUrl).openConnection() as java.net.HttpURLConnection).apply {
        connectTimeout = 6000
        readTimeout = 6000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "mafari")
        setRequestProperty("Accept", "application/vnd.github+json")
    }
    try {
        if (connection.responseCode !in 200..299) return@runCatching null
        val text = connection.inputStream.bufferedReader().readText()
        val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
            ?: return@runCatching null
        val pageUrl = Regex("\"html_url\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
            ?: PROJECT_URL + "/releases"
        val apkUrl = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"")
            .find(text)?.groupValues?.get(1)
        val notes = runCatching { org.json.JSONObject(text).optString("body", "") }.getOrDefault("").trim()
        AppRelease(version = tag, pageUrl = pageUrl, apkUrl = apkUrl, notes = notes)
    } finally {
        connection.disconnect()
    }
}.getOrNull()

@Composable
private fun AboutRow(title: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurface)
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
