package dev.dsh.miuixbrowser

import android.content.Intent
import android.net.Uri
import android.webkit.WebSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val kernel = webViewVersion() ?: "未知"
    val ua = remember { runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault("未知") }

    val backdrop = rememberLayerBackdrop()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(MiuixTheme.colorScheme.surface)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(top = statusTop + 56.dp)
                .padding(bottom = 24.dp),
        ) {
            SmallTitle(text = "版本")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                insideMargin = PaddingValues(0.dp),
            ) {
                AboutRow("应用版本", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
                AboutRow("MIUIX", "0.9.4")
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
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/rosenii/mafari")),
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

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Mafari · 基于 MIUIX 与系统 WebView",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp + statusTop)
                .progressiveTopBar(
                    backdrop = backdrop,
                    tint = MiuixTheme.colorScheme.surface.copy(alpha = 0.55f),
                ),
        ) {
            SmallTopAppBar(
                title = "关于",
                color = Color.Transparent,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                defaultWindowInsetsPadding = false,
                modifier = Modifier.statusBarsPadding(),
            )
        }
    }
}

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
