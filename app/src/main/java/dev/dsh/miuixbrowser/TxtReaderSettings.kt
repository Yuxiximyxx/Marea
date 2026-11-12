package dev.dsh.miuixbrowser

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

/**
 * 阅读设置导航状态（fork.29）：统一设置组件的二级页状态，书内弹层与
 * 书架「阅读设置」页共用同一个状态类——返回一级时宿主清掉 section。
 */
class TxtReaderSettingsNav {
    var section by mutableStateOf<String?>(null)

    companion object {
        const val SECTION_BOOK_RULE = "book_rule"
        const val SECTION_ENCODING = "encoding"
        const val SECTION_RULES = "rules"
        const val SECTION_PAGE_DISPLAY = "page_display"
        const val SECTION_TYPESET = "typeset"
        const val SECTION_BG = "bg"
    }
}

/**
 * 书内设置上下文（fork.29）：打开了具体某本书时才有的每本书设置
 * （本书目录规则、编码）。书架设置页没有当前书，传 null，相应入口不出现。
 */
class TxtSettingsBookContext(
    val book: TxtBook,
    val bookName: String,
    val encodingLabel: String,
    val encodingChoice: String?,
    val onEncodingChange: (String) -> Unit,
    val tocRules: List<TxtTocRule>,
    val tocRuleChoice: Long?,
    val onTocRuleChoice: (Long?) -> Unit,
    val onTocRulesChange: (List<TxtTocRule>) -> Unit,
)

private fun txtReaderSectionTitle(section: String?): String = when (section) {
    TxtReaderSettingsNav.SECTION_BOOK_RULE -> "本书目录规则"
    TxtReaderSettingsNav.SECTION_ENCODING -> "编码"
    TxtReaderSettingsNav.SECTION_RULES -> "目录规则"
    TxtReaderSettingsNav.SECTION_PAGE_DISPLAY -> "翻页与显示"
    TxtReaderSettingsNav.SECTION_TYPESET -> "排版"
    TxtReaderSettingsNav.SECTION_BG -> "背景"
    else -> "设置"
}

private fun txtReaderBgLabel(preset: String): String = when (preset) {
    TxtReaderStore.BG_CREAM -> "米白"
    TxtReaderStore.BG_GREEN -> "护眼绿"
    TxtReaderStore.BG_NIGHT -> "深夜灰"
    else -> "跟随主题"
}

/**
 * 统一阅读设置内容（fork.29 核心）：一套内容、一处数据源（TxtReaderStore /
 * TxtTocRules 的单份存储）。书架「阅读设置」页以整页承载、书内「设置」以
 * 底部弹层承载，渲染的是这同一个组件，不再有第二份设置实现。
 *
 * 分层按 Legado：一级只放高频滑杆（亮度、字号）与入口行；本书目录规则、
 * 编码、目录规则、翻页与显示、排版、背景全部收进二级页（[nav] 切换）。
 */
@Composable
fun TxtReaderSettingsContent(
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    onFontSizeCommit: () -> Unit,
    brightness: Float,
    systemBrightness: Float,
    onBrightnessChange: (Float) -> Unit,
    onBrightnessCommit: () -> Unit,
    readMode: String,
    onReadModeChange: (String) -> Unit,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    lineHeight: Float,
    onLineHeightChange: (Float) -> Unit,
    paraSpacing: Float,
    onParaSpacingChange: (Float) -> Unit,
    marginDp: Float,
    onMarginChange: (Float) -> Unit,
    onTypesetCommit: () -> Unit,
    readerBg: String,
    onReaderBgChange: (String) -> Unit,
    tocRulesCount: Int,
    tocRulesEnabled: Int,
    onOpenTocRulesPage: (() -> Unit)?,
    glassBackdrop: LayerBackdrop? = null,
    glassLiquid: com.kyant.backdrop.Backdrop? = null,
    glassBlurEnabled: Boolean = true,
    book: TxtSettingsBookContext?,
    nav: TxtReaderSettingsNav,
    modifier: Modifier = Modifier,
) {
    var brightnessDrag by remember { mutableStateOf<Float?>(null) }
    Column(modifier = modifier) {
        if (nav.section == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
            ) {
                ReaderSettingsSectionTitle("阅读")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "亮度",
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (brightness >= 0f) {
                                TextButton(
                                    text = "跟随系统",
                                    onClick = {
                                        onBrightnessChange(-1f)
                                        onBrightnessCommit()
                                    },
                                )
                            } else {
                                Text(
                                    text = "跟随系统",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        Slider(
                            value = brightnessDrag
                                ?: if (brightness >= 0f) brightness else systemBrightness,
                            onValueChange = { brightnessDrag = it },
                            onValueChangeFinished = {
                                brightnessDrag?.let { value ->
                                    onBrightnessChange(value)
                                    onBrightnessCommit()
                                }
                                brightnessDrag = null
                            },
                            valueRange = 0.05f..1f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "字号",
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${fontSize.toInt()}sp",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Slider(
                            value = fontSize,
                            onValueChange = onFontSizeChange,
                            onValueChangeFinished = onFontSizeCommit,
                            valueRange = 15f..30f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                ReaderSettingsSectionTitle("设置")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Column {
                        if (book != null) {
                            ReaderEntryRow(
                                title = "本书目录规则",
                                summary = book.tocRuleChoice?.let { id ->
                                    book.tocRules.firstOrNull { it.id == id }?.name
                                } ?: "自动（全局顺序）",
                                onClick = { nav.section = TxtReaderSettingsNav.SECTION_BOOK_RULE },
                            )
                            ReaderEntryRow(
                                title = "编码",
                                summary = if (book.encodingChoice == null) {
                                    "自动（识别为 ${book.encodingLabel}）"
                                } else {
                                    book.encodingChoice
                                },
                                onClick = { nav.section = TxtReaderSettingsNav.SECTION_ENCODING },
                            )
                        }
                        ReaderEntryRow(
                            title = "目录规则",
                            summary = "共 $tocRulesCount 条，启用 $tocRulesEnabled 条",
                            onClick = {
                                if (onOpenTocRulesPage != null) {
                                    onOpenTocRulesPage()
                                } else {
                                    nav.section = TxtReaderSettingsNav.SECTION_RULES
                                }
                            },
                        )
                        ReaderEntryRow(
                            title = "翻页与显示",
                            summary = "${if (readMode == TxtReaderStore.MODE_PAGE) "左右翻页" else "上下滚动"}" +
                                " · 全屏${if (fullscreen) "开" else "关"}",
                            onClick = { nav.section = TxtReaderSettingsNav.SECTION_PAGE_DISPLAY },
                        )
                        ReaderEntryRow(
                            title = "排版",
                            summary = "行距 ${String.format(Locale.US, "%.2f", lineHeight)}" +
                                " · 段距 ${paraSpacing.toInt()} · 边距 ${marginDp.toInt()}",
                            onClick = { nav.section = TxtReaderSettingsNav.SECTION_TYPESET },
                        )
                        ReaderEntryRow(
                            title = "背景",
                            summary = txtReaderBgLabel(readerBg),
                            onClick = { nav.section = TxtReaderSettingsNav.SECTION_BG },
                        )
                    }
                }
                if (book == null) {
                    Text(
                        text = "每本书的目录规则与编码，在打开书籍后于书内「设置」里单独调整。",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    text = "‹ 返回",
                    onClick = { nav.section = null },
                )
                Text(
                    text = txtReaderSectionTitle(nav.section),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
            when (nav.section) {
                TxtReaderSettingsNav.SECTION_BOOK_RULE -> if (book != null) {
                    TxtBookRulePickerContent(
                        book = book.book,
                        rules = book.tocRules,
                        choice = book.tocRuleChoice,
                        onPick = { id -> book.onTocRuleChoice(id) },
                        modifier = Modifier.weight(1f),
                    )
                }

                TxtReaderSettingsNav.SECTION_ENCODING -> if (book != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = "当前自动识别为 ${book.encodingLabel}；识别不对时手动指定，会按新编码重新解码。",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                        val selected = book.encodingChoice ?: TxtCodec.ENCODING_AUTO
                        TxtCodec.ENCODING_OPTIONS.forEach { option ->
                            val isSelected = option == selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (option != selected) book.onEncodingChange(option)
                                    }
                                    .padding(horizontal = 4.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (isSelected) "●" else "○",
                                    fontSize = 13.sp,
                                    color = if (isSelected) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = option,
                                    fontSize = 15.sp,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                TxtReaderSettingsNav.SECTION_RULES -> if (book != null) {
                    TxtTocRulesManager(
                        rules = book.tocRules,
                        onRulesChange = book.onTocRulesChange,
                        modifier = Modifier.weight(1f),
                        glassBackdrop = glassBackdrop,
                        glassLiquid = glassLiquid,
                        glassBlurEnabled = glassBlurEnabled,
                    )
                }

                TxtReaderSettingsNav.SECTION_PAGE_DISPLAY -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp),
                    ) {
                        Text(
                            text = "翻页模式",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            ReaderSettingsChip(
                                text = "滚动",
                                selected = readMode == TxtReaderStore.MODE_SCROLL,
                                onClick = { onReadModeChange(TxtReaderStore.MODE_SCROLL) },
                            )
                            Spacer(Modifier.width(8.dp))
                            ReaderSettingsChip(
                                text = "左右翻页",
                                selected = readMode == TxtReaderStore.MODE_PAGE,
                                onClick = { onReadModeChange(TxtReaderStore.MODE_PAGE) },
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "全屏阅读",
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "隐藏应用顶/底栏；系统状态栏与导航条保持显示，页面背景延伸到系统栏之下",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            ReaderSettingsChip(
                                text = if (fullscreen) "开" else "关",
                                selected = fullscreen,
                                onClick = { onFullscreenChange(!fullscreen) },
                            )
                        }
                    }
                }

                TxtReaderSettingsNav.SECTION_TYPESET -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp),
                    ) {
                        ReaderTypesetSlider(
                            label = "行距",
                            valueText = String.format(Locale.US, "%.2f 倍", lineHeight),
                            value = lineHeight,
                            valueRange = 1.3f..2.4f,
                            onValueChange = onLineHeightChange,
                            onCommit = onTypesetCommit,
                        )
                        ReaderTypesetSlider(
                            label = "段间距",
                            valueText = "${paraSpacing.toInt()}dp",
                            value = paraSpacing,
                            valueRange = 0f..24f,
                            onValueChange = onParaSpacingChange,
                            onCommit = onTypesetCommit,
                        )
                        ReaderTypesetSlider(
                            label = "左右边距",
                            valueText = "${marginDp.toInt()}dp",
                            value = marginDp,
                            valueRange = 8f..48f,
                            onValueChange = onMarginChange,
                            onCommit = onTypesetCommit,
                        )
                        Text(
                            text = "排版对滚动与左右翻页两种模式同时生效，调整后分页立即重排，阅读位置保持不变。",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                TxtReaderSettingsNav.SECTION_BG -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf(
                                TxtReaderStore.BG_THEME,
                                TxtReaderStore.BG_CREAM,
                                TxtReaderStore.BG_GREEN,
                                TxtReaderStore.BG_NIGHT,
                            ).forEach { preset ->
                                val isSelected = readerBg == preset
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                txtReaderBgColor(preset)
                                                    ?: MiuixTheme.colorScheme.surface,
                                            )
                                            .then(
                                                if (isSelected) {
                                                    Modifier.border(
                                                        2.dp,
                                                        MiuixTheme.colorScheme.primary,
                                                        RoundedCornerShape(14.dp),
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .clickable { onReaderBgChange(preset) },
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = txtReaderBgLabel(preset),
                                        fontSize = 12.sp,
                                        color = if (isSelected) MiuixTheme.colorScheme.primary
                                        else MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                        Text(
                            text = "仅作用于阅读页的底色与字色，不影响应用其他页面。",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderTypesetSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueText,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onCommit,
        valueRange = valueRange,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ReaderEntryRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Text(
            text = "›",
            fontSize = 18.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/**
 * 阅读设置页（fork.29）：书架页齿轮进入的整页宿主，承载统一的
 * [TxtReaderSettingsContent]，与书内设置弹层同一组件、同一数据源。
 * 本页无当前书语境，本书目录规则 / 编码不在此出现；目录规则走独立页。
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
    var lineHeight by remember { mutableStateOf(TxtReaderStore.lineHeight(appContext)) }
    var paraSpacing by remember { mutableStateOf(TxtReaderStore.paraSpacing(appContext)) }
    var marginDp by remember { mutableStateOf(TxtReaderStore.margin(appContext)) }
    var readerBg by remember { mutableStateOf(TxtReaderStore.bgPreset(appContext)) }
    val nav = remember { TxtReaderSettingsNav() }
    val systemBrightness = remember {
        runCatching {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS, 128,
            ) / 255f
        }.getOrDefault(0.55f)
    }
    val tocRules = TxtTocRules.load(appContext)

    BackHandler(enabled = nav.section != null) { nav.section = null }

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
        TxtReaderSettingsContent(
            fontSize = fontSize,
            onFontSizeChange = { fontSize = it },
            onFontSizeCommit = { TxtReaderStore.setFontSize(appContext, fontSize) },
            brightness = brightness,
            systemBrightness = systemBrightness,
            onBrightnessChange = { brightness = it },
            onBrightnessCommit = { TxtReaderStore.setBrightness(appContext, brightness) },
            readMode = readMode,
            onReadModeChange = { value ->
                readMode = value
                TxtReaderStore.setReadMode(appContext, value)
            },
            fullscreen = fullscreen,
            onFullscreenChange = { value ->
                fullscreen = value
                TxtReaderStore.setFullscreen(appContext, value)
            },
            lineHeight = lineHeight,
            onLineHeightChange = { lineHeight = it },
            paraSpacing = paraSpacing,
            onParaSpacingChange = { paraSpacing = it },
            marginDp = marginDp,
            onMarginChange = { marginDp = it },
            onTypesetCommit = {
                TxtReaderStore.setLineHeight(appContext, lineHeight)
                TxtReaderStore.setParaSpacing(appContext, paraSpacing)
                TxtReaderStore.setMargin(appContext, marginDp)
            },
            readerBg = readerBg,
            onReaderBgChange = { value ->
                readerBg = value
                TxtReaderStore.setBgPreset(appContext, value)
            },
            tocRulesCount = tocRules.size,
            tocRulesEnabled = tocRules.count { it.enabled },
            onOpenTocRulesPage = onOpenTocRules,
            book = null,
            nav = nav,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

/**
 * 目录规则页：阅读设置下的独立页（书架侧入口）；主体与书内设置弹层的
 * 「目录规则」二级页共用 TxtTocRulesManager，数据同一份。
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
