package dev.dsh.miuixbrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 阅读设置页（fork.28）：TXT 阅读器设置的统一落点，从书架页齿轮进入。
 * 汇总既有阅读偏好（字号 / 翻页模式 / 全屏 / 亮度）与目录规则入口；
 * 数据走 TxtReaderStore / TxtTocRules 的既有单份存储，与阅读器内
 * 快速设置面板完全同源、不分叉。今后新的阅读功能设置在此页续加分区。
 */
@Composable
fun TxtReaderSettingsScreen(
    onBack: () -> Unit,
    onOpenTocRules: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    var fontSize by remember { mutableStateOf(TxtReaderStore.fontSize(appContext)) }
    var readMode by remember { mutableStateOf(TxtReaderStore.readMode(appContext)) }
    var fullscreen by remember { mutableStateOf(TxtReaderStore.fullscreen(appContext)) }
    var brightness by remember { mutableStateOf(TxtReaderStore.brightness(appContext)) }
    var brightnessDrag by remember { mutableStateOf<Float?>(null) }
    val tocRules = remember { TxtTocRules.load(appContext) }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "阅读设置",
                subtitle = "TXT 阅读器的全部设置",
                navigationIcon = {
                    LiquidGlassBackButton(backdrop = null, surfaceVisible = true, onClick = onBack)
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            ReaderSettingsSectionTitle("阅读")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column {
                    Text(
                        text = "字号 ${fontSize.toInt()}sp",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        onValueChangeFinished = {
                            TxtReaderStore.setFontSize(appContext, fontSize)
                        },
                        valueRange = 15f..30f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "翻页模式",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        ReaderSettingsChip(
                            text = "滚动",
                            selected = readMode == TxtReaderStore.MODE_SCROLL,
                            onClick = {
                                readMode = TxtReaderStore.MODE_SCROLL
                                TxtReaderStore.setReadMode(appContext, readMode)
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        ReaderSettingsChip(
                            text = "左右翻页",
                            selected = readMode == TxtReaderStore.MODE_PAGE,
                            onClick = {
                                readMode = TxtReaderStore.MODE_PAGE
                                TxtReaderStore.setReadMode(appContext, readMode)
                            },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "全屏阅读",
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "隐藏应用顶/底栏，系统状态栏与导航条保持显示",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        ReaderSettingsChip(
                            text = if (fullscreen) "开" else "关",
                            selected = fullscreen,
                            onClick = {
                                fullscreen = !fullscreen
                                TxtReaderStore.setFullscreen(appContext, fullscreen)
                            },
                        )
                    }
                }
            }

            ReaderSettingsSectionTitle("亮度")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column {
                    Text(
                        text = if (brightness >= 0f) {
                            "阅读亮度 ${(brightness * 100).toInt()}%"
                        } else {
                            "阅读亮度（跟随系统）"
                        },
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Slider(
                        value = brightnessDrag
                            ?: if (brightness >= 0f) brightness else 0.55f,
                        onValueChange = { brightnessDrag = it },
                        onValueChangeFinished = {
                            brightnessDrag?.let { value ->
                                brightness = value
                                TxtReaderStore.setBrightness(appContext, value)
                            }
                            brightnessDrag = null
                        },
                        valueRange = 0.05f..1f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        text = "跟随系统",
                        onClick = {
                            brightness = -1f
                            TxtReaderStore.setBrightness(appContext, -1f)
                        },
                        enabled = brightness >= 0f,
                    )
                }
            }

            ReaderSettingsSectionTitle("目录")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column {
                    TextButton(
                        text = "目录规则（共 ${tocRules.size} 条，启用 ${tocRules.count { it.enabled }} 条）",
                        onClick = onOpenTocRules,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "每本书还可在阅读器设置的「本书目录规则」里单独指定一条，并预览它在本书上的切章结果。",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            // 后续阅读功能的新设置分区加在这里（统一落点口径，fork.28）
        }
    }
}

/**
 * 目录规则页（fork.28）：阅读设置下的独立页，是规则管理的正主入口；
 * 主体与阅读器内覆盖层共用 TxtTocRulesManager，数据同一份。
 */
@Composable
fun TxtTocRulesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    var rules by remember { mutableStateOf(TxtTocRules.load(appContext)) }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "目录规则",
                subtitle = "按列表顺序匹配，首中即为章节",
                navigationIcon = {
                    LiquidGlassBackButton(backdrop = null, surfaceVisible = true, onClick = onBack)
                },
            )
        },
    ) { innerPadding ->
        TxtTocRulesManager(
            rules = rules,
            onRulesChange = { updated ->
                rules = updated
                TxtTocRules.save(appContext, updated)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@Composable
private fun ReaderSettingsSectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun ReaderSettingsChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = if (selected) MiuixTheme.colorScheme.onPrimary
            else MiuixTheme.colorScheme.onSurface,
        )
    }
}
