package dev.dsh.miuixbrowser

import android.content.Intent
import android.net.Uri
import android.webkit.WebSettings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.derivedStateOf

@Composable
fun AboutScreen(onBack: () -> Unit, blurEnabled: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kernel = webViewVersion() ?: "未知"
    val ua = remember { runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault("未知") }
    var checkingUpdate by remember { mutableStateOf(false) }

    val pageBackdrop = rememberLayerBackdrop()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val surface = MiuixTheme.colorScheme.surface
    val iconShape = RoundedCornerShape(26.dp)
    val scrollState = rememberScrollState()
    val scrollBehavior = MiuixScrollBehavior()
    val isScrolled by remember { derivedStateOf { scrollState.value > 0 } }
    val isInDark = isAppDarkTheme()

    // demo AboutPage / ForegroundBlurDemo 的标题 blend 配色
    val titleBlend = remember(isInDark) {
        if (isInDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(surface)) {
        // ---- 全屏背景采样：主题色渐变，供 foreground blur / 玻璃顶栏取样 ----
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
                .verticalScroll(scrollState)
                .padding(top = statusTop + 56.dp)
                .padding(bottom = 24.dp),
        ) {
            // ---- 头部：大图标 + 检查更新 + 全屏采样上的 foreground blur 标题 ----
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh, iconShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface),
                        modifier = Modifier.size(58.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))

                // miuix-glass 风格检查更新（大图标下方）
                GlassDialogButton(
                    onClick = {
                        if (checkingUpdate) return@GlassDialogButton
                        checkingUpdate = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { fetchLatestVersion() }
                            checkingUpdate = false
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
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(result.second)),
                                        )
                                    }
                                }
                            }
                        }
                    },
                    role = GlassDialogButtonRole.Primary,
                    modifier = Modifier.width(200.dp),
                ) {
                    Text(
                        text = if (checkingUpdate) "检查中…" else "检查更新",
                        style = MiuixTheme.textStyles.button,
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Foreground blur：文字 alpha 作为遮罩，从全屏 backdrop 毛玻璃化（demo DstIn）
                Text(
                    text = "Mafari",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .then(
                            if (blurEnabled) {
                                Modifier.textureBlur(
                                    backdrop = pageBackdrop,
                                    shape = RoundedCornerShape(16.dp),
                                    blurRadius = 150f,
                                    noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                    colors = BlurDefaults.blurColors(blendColors = titleBlend),
                                    contentBlendMode = ComposeBlendMode.DstIn,
                                )
                            } else {
                                Modifier
                            },
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "版本 " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
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
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)),
                                )
                            }
                        }
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

        GlassPageTopBar(
            title = "关于",
            backdrop = pageBackdrop,
            onBack = onBack,
            progressiveBlur = blurEnabled,
            scrollBehavior = scrollBehavior,
            isContentScrolled = isScrolled,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

private const val PROJECT_URL = "https://github.com/rosenii/mafari"
private const val RELEASE_API = "https://api.github.com/repos/rosenii/mafari/releases/latest"

internal fun fetchLatestVersion(): Pair<String, String>? = runCatching {
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
