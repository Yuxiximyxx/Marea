package dev.dsh.miuixbrowser

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberKyantBackdrop

private val MATCH_TYPE_LABELS = listOf("正则（整行）", "域名", "包含子串")
private val ACTION_LABELS = listOf("打开（VIEW）", "分享文本（SEND）")

private fun ruleSummary(rule: DispatchRule): String {
    val scope = buildList {
        if (DispatchScope.CLICK in rule.scopes) add("点击")
        if (DispatchScope.DOWNLOAD in rule.scopes) add("下载")
    }.joinToString("+").ifBlank { "无作用域" }
    val target = rule.targetPackage.ifBlank { "系统选择" }
    return "$scope · ${MATCH_TYPE_LABELS.getOrElse(rule.matchType) { "正则" }} · → $target"
}

@Composable
fun DispatchScreen(
    onBack: () -> Unit,
    blurEnabled: Boolean = true,
) {
    val backdrop = rememberLayerBackdrop()
    val liquidBackdrop = rememberKyantBackdrop()
    val glass = rememberGlassActive(blurEnabled)
    val liquid = if (glass) liquidBackdrop else null
    val scrollBehavior = MiuixScrollBehavior()
    val topScrolled = rememberTopBarScrolled(scrollBehavior)
    var editing by remember { mutableStateOf<DispatchRule?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var importReport by remember { mutableStateOf<LinkDispatch.ImportReport?>(null) }
    val context = LocalContext.current
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            }.getOrNull()
            importReport = if (text.isNullOrBlank()) {
                LinkDispatch.ImportReport("", 0, 0, emptyList(), 0, "读取文件失败或文件为空")
            } else {
                LinkDispatch.importFromJson(text)
            }
        }
    }

    CompositionLocalProvider(LocalLiquidBackdrop provides if (glass) liquidBackdrop else null) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                PageGlassTopBar(
                    title = "链接分发",
                    backdrop = backdrop,
                    glass = glass,
                    scrollBehavior = scrollBehavior,
                    onBack = onBack,
                    backButton = {
                        LiquidGlassBackButton(backdrop = liquid, surfaceVisible = topScrolled, onClick = onBack)
                    },
                )
            },
        ) { innerPadding ->
            val topInset = innerPadding.calculateTopPadding()
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop)
                        .kyantLayerBackdrop(liquidBackdrop)
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .background(MiuixTheme.colorScheme.surface),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = topInset,
                            bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        ),
                    ) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 8.dp),
                                insideMargin = PaddingValues(0.dp),
                            ) {
                                AppSwitchPreference(
                                    title = "链接分发",
                                    summary = "点击链接或触发下载时，按规则把链接送到指定应用；送出失败自动回退站内打开/内置下载",
                                    checked = LinkDispatch.enabled,
                                    onCheckedChange = { LinkDispatch.updateEnabled(it) },
                                )
                            }
                        }
                        item { SmallTitle(text = "规则（自上而下优先，首中即停）") }
                        if (LinkDispatch.rules.isEmpty()) {
                            item {
                                Text(
                                    text = "还没有规则。点下方「添加规则」新建，或恢复预置规则包。",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                        items(LinkDispatch.rules.size) { index ->
                            val rule = LinkDispatch.rules[index]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 8.dp),
                                insideMargin = PaddingValues(14.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                editing = rule
                                                editorOpen = true
                                            },
                                    ) {
                                        Text(
                                            text = rule.name.ifBlank { "未命名规则" } + if (rule.builtin) "（预置）" else "",
                                            fontSize = 15.sp,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = ruleSummary(rule),
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = rule.pattern,
                                            fontSize = 11.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    AppSwitch(
                                        checked = rule.enabled,
                                        onCheckedChange = { LinkDispatch.upsert(rule.copy(enabled = it)) },
                                    )
                                }
                            }
                        }
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 8.dp),
                                insideMargin = PaddingValues(14.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            editing = null
                                            editorOpen = true
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "添加规则",
                                        fontSize = 15.sp,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 8.dp),
                                insideMargin = PaddingValues(14.dp),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { importPicker.launch(arrayOf("application/json", "*/*")) },
                                ) {
                                    Text(
                                        text = "从文件导入",
                                        fontSize = 15.sp,
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = "支持 LinkGo 导出 JSON 与 Marea 自有格式；导入条目追加到列表末尾，LinkGo 专有能力（短链预解析、模板变换）会降级并在报告中注明",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            RuleEditorDialog(
                show = editorOpen,
                rule = editing,
                backdrop = if (glass) backdrop else null,
                onDismiss = { editorOpen = false },
                onSave = { saved ->
                    LinkDispatch.upsert(saved)
                    editorOpen = false
                },
                onDelete = { target ->
                    LinkDispatch.remove(target)
                    editorOpen = false
                },
                onMove = { target, dir -> LinkDispatch.move(target, dir) },
            )

            ImportReportDialog(
                show = importReport != null,
                report = importReport,
                backdrop = if (glass) backdrop else null,
                onDismiss = { importReport = null },
            )
        }
    }
}

/** 导入报告：成功/降级计数 + 降级逐条原因（LinkGo 语义与 Marea 有差异的部分都在这里摊开） */
@Composable
private fun ImportReportDialog(
    show: Boolean,
    report: LinkDispatch.ImportReport?,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    onDismiss: () -> Unit,
) {
    AppGlassDialog(
        show = show,
        title = "导入报告",
        summary = if (report?.error != null) "导入没有完成" else "导入的规则已追加到列表末尾",
        backdrop = backdrop,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (report == null) {
                // 占位，show=false 时不会展示
            } else if (report.error != null) {
                Text(
                    text = report.error,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.error,
                )
            } else {
                Text(
                    text = "来源格式：${report.format} 导出文件，共 ${report.total} 条",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "成功导入 ${report.added} 条，其中 ${report.degraded.size} 条有降级",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                if (report.proprietaryDropped > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "另有 ${report.proprietaryDropped} 条携带的 LinkGo 专有设置（预热、启动模式、最近任务排除等）已忽略",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (report.degraded.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    report.degraded.forEach { d ->
                        Text(
                            text = "· ${d.name}：${d.reason}",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            AppDialogButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(text = "知道了", style = MiuixTheme.textStyles.button)
            }
        }
    }
}

@Composable
private fun RuleEditorDialog(
    show: Boolean,
    rule: DispatchRule?,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    onDismiss: () -> Unit,
    onSave: (DispatchRule) -> Unit,
    onDelete: (DispatchRule) -> Unit,
    onMove: (DispatchRule, Int) -> Unit,
) {
    val key = (if (show) "open" else "closed") + (rule?.id ?: "new")
    var name by remember(key) { mutableStateOf(rule?.name ?: "") }
    var matchType by remember(key) { mutableStateOf(rule?.matchType ?: 0) }
    var pattern by remember(key) { mutableStateOf(rule?.pattern ?: "") }
    var rewrite by remember(key) { mutableStateOf(rule?.rewrite ?: "") }
    var pkg by remember(key) { mutableStateOf(rule?.targetPackage ?: "") }
    var act by remember(key) { mutableStateOf(rule?.targetActivity ?: "") }
    var action by remember(key) { mutableStateOf(rule?.action ?: 0) }
    var scopeClick by remember(key) { mutableStateOf(rule?.scopes?.contains(DispatchScope.CLICK) ?: false) }
    var scopeDownload by remember(key) { mutableStateOf(rule?.scopes?.contains(DispatchScope.DOWNLOAD) ?: true) }
    var confirm by remember(key) { mutableStateOf(rule?.confirm ?: false) }
    var ruleEnabled by remember(key) { mutableStateOf(rule?.enabled ?: true) }
    var testUrl by remember(key) { mutableStateOf("") }

    val draft = DispatchRule(
        id = rule?.id ?: java.util.UUID.randomUUID().toString(),
        name = name.trim().ifBlank { "未命名规则" },
        enabled = ruleEnabled,
        priority = rule?.priority ?: Int.MAX_VALUE,
        scopes = buildSet {
            if (scopeClick) add(DispatchScope.CLICK)
            if (scopeDownload) add(DispatchScope.DOWNLOAD)
        },
        matchType = matchType,
        pattern = pattern.trim(),
        rewrite = rewrite.trim(),
        targetPackage = pkg.trim(),
        targetActivity = act.trim(),
        action = action,
        confirm = confirm,
        builtin = rule?.builtin ?: false,
    )
    val patternError = LinkDispatch.patternError(draft)
    val testResult = if (testUrl.isNotBlank() && patternError == null) {
        LinkDispatch.applyMatch(draft, testUrl.trim())
    } else {
        null
    }
    val canSave = pattern.isNotBlank() && patternError == null && draft.scopes.isNotEmpty()
    val context = LocalContext.current

    AppGlassDialog(
        show = show,
        title = if (rule == null) "添加规则" else "编辑规则",
        summary = "命中后把链接（可先改写）送到目标应用；类名留空由系统在目标应用内路由",
        backdrop = backdrop,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            TextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = "规则名称",
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            AppDropdownPreference(
                backdrop = null,
                title = "匹配方式",
                items = MATCH_TYPE_LABELS,
                selectedIndex = matchType,
                onSelectedIndexChange = { matchType = it },
            )
            TextField(
                value = pattern,
                onValueChange = { pattern = it },
                modifier = Modifier.fillMaxWidth(),
                label = when (matchType) {
                    1 -> "域名（如 example.com，含子域名）"
                    2 -> "链接包含的子串"
                    else -> "正则（整行匹配）"
                },
                singleLine = true,
            )
            if (patternError != null) {
                Text(
                    text = patternError,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            TextField(
                value = rewrite,
                onValueChange = { rewrite = it },
                modifier = Modifier.fillMaxWidth(),
                label = "改写模板（可空，支持 $1 / $name / $0）",
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            TextField(
                value = pkg,
                onValueChange = { pkg = it },
                modifier = Modifier.fillMaxWidth(),
                label = "目标应用包名（可空 = 系统选择）",
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            TextField(
                value = act,
                onValueChange = { act = it },
                modifier = Modifier.fillMaxWidth(),
                label = "目标类名（可空）",
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            AppDropdownPreference(
                backdrop = null,
                title = "送出方式",
                items = ACTION_LABELS,
                selectedIndex = action,
                onSelectedIndexChange = { action = it },
            )
            AppSwitchPreference(
                title = "作用于链接点击",
                checked = scopeClick,
                onCheckedChange = { scopeClick = it },
            )
            AppSwitchPreference(
                title = "作用于下载触发",
                checked = scopeDownload,
                onCheckedChange = { scopeDownload = it },
            )
            AppSwitchPreference(
                title = "命中先确认",
                summary = "命中时先弹窗确认，拒绝则站内打开/内置下载",
                checked = confirm,
                onCheckedChange = { confirm = it },
            )
            AppSwitchPreference(
                title = "启用这条规则",
                checked = ruleEnabled,
                onCheckedChange = { ruleEnabled = it },
            )
            Spacer(Modifier.height(8.dp))
            TextField(
                value = testUrl,
                onValueChange = { testUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = "测试链接（实时预览命中与改写结果）",
                singleLine = true,
            )
            if (testUrl.isNotBlank()) {
                Text(
                    text = when {
                        patternError != null -> patternError
                        testResult != null -> "命中 → $testResult"
                        else -> "未命中"
                    },
                    fontSize = 12.sp,
                    color = if (testResult != null) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (rule != null) {
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppTextButton(
                        text = "上移",
                        onClick = { onMove(rule, -1) },
                        modifier = Modifier.weight(1f),
                    )
                    AppTextButton(
                        text = "下移",
                        onClick = { onMove(rule, 1) },
                        modifier = Modifier.weight(1f),
                    )
                    AppTextButton(
                        text = "删除",
                        onClick = { onDelete(rule) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppDialogButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(text = "取消", style = MiuixTheme.textStyles.button)
                }
                AppDialogButton(
                    onClick = {
                        if (canSave) {
                            onSave(draft)
                        } else {
                            android.widget.Toast.makeText(context, "请先填写有效的匹配模式并至少选一个作用域", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    role = GlassDialogButtonRole.Primary,
                ) {
                    Text(text = "保存", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}
