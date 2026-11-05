package dev.dsh.miuixbrowser

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.More
import top.yukonga.miuix.kmp.icon.glass.Refresh
import top.yukonga.miuix.kmp.glass.GlassPopup
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import top.yukonga.miuix.kmp.icon.glass.File
import top.yukonga.miuix.kmp.icon.glass.Download
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.Add
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberKyantBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll

@Composable
fun ExtensionsScreen(store: ExtensionStore, onBack: () -> Unit, blurEnabled: Boolean = true) {
    var editing by remember { mutableStateOf<UserScript?>(null) }
    var creating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var updatingAll by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<UserScript?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val backdrop = rememberLayerBackdrop()
    val liquidBackdrop = rememberKyantBackdrop()
    val liquid = if (rememberGlassActive(blurEnabled)) liquidBackdrop else null
    val glass = rememberGlassActive(blurEnabled)
    val deleteTarget = deleting
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val topScrolled = rememberTopBarScrolled(scrollBehavior)
    var showMore by remember { mutableStateOf(false) }
    var moreAnchor by remember { mutableStateOf(Rect.Zero) }
    val updateAll: () -> Unit = {
        if (!updatingAll) {
            val targets = store.items.filter { it.sourceUrl.isNotBlank() }
            if (targets.isEmpty()) {
                android.widget.Toast.makeText(context, "没有可更新的脚本", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                updatingAll = true
                busy = true
                scope.launch {
                    var ok = 0
                    targets.forEach { target ->
                        val fresh = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            fetchScript(target.sourceUrl)
                        }
                        if (fresh != null) {
                            store.update(target.id, target.name, fresh, target.enabled, target.match)
                            ok++
                        }
                    }
                    busy = false
                    updatingAll = false
                    android.widget.Toast.makeText(
                        context,
                        "已更新 $ok/${targets.size} 个脚本",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageGlassTopBar(
                title = "脚本",
                backdrop = backdrop,
                glass = glass,
                scrollBehavior = scrollBehavior,
                onBack = onBack,
                backButton = {
                    LiquidGlassBackButton(backdrop = liquid, surfaceVisible = topScrolled, onClick = onBack)
                },
                actions = {
                    LiquidGlassButton(
                        onClick = { showMore = true },
                        backdrop = liquid,
                        surfaceVisible = topScrolled,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .onGloballyPositioned { moreAnchor = it.boundsInRoot() },
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Glass.More,
                            contentDescription = "更多",
                            modifier = Modifier.size(22.dp),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
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
                    .background(MiuixTheme.colorScheme.surface)
                    .navigationBarsPadding(),
            ) {
                val normalScripts = store.items.toList()
                val missingBuiltins = BUILTIN_SCRIPTS.filter { builtin ->
                    store.items.none { item ->
                        builtin.userScriptUrl.isNotBlank() && item.sourceUrl == builtin.userScriptUrl
                    }
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = topInset + 8.dp, bottom = 88.dp),
                ) {
                    if (missingBuiltins.isNotEmpty()) {
                        item {
                            SmallTitle(text = "建议安装的脚本")
                        }
                        items(missingBuiltins.size) { index ->
                            val builtin = missingBuiltins[index]
                            BuiltinScriptCard(builtin) {
                                if (!busy) {
                                    busy = true
                                    scope.launch {
                                        if (builtin.userScriptUrl.isNotBlank()) {
                                            val code = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                fetchScript(builtin.userScriptUrl)
                                            }
                                            if (code != null) {
                                                store.add("", code, sourceUrl = builtin.userScriptUrl)
                                                android.widget.Toast.makeText(context, "已添加 " + builtin.name, android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                android.widget.Toast.makeText(context, "下载失败，检查网络", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            store.add(builtin.name, builtin.code)
                                        }
                                        busy = false
                                    }
                                }
                            }
                        }
                    }
                    if (normalScripts.isEmpty()) {
                        item {
                            Text(
                                text = "还没有脚本。脚本就是一段在页面加载完成后注入的 JavaScript。",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    } else if (normalScripts.isNotEmpty()) {
                        item {
                            SmallTitle(text = "已安装脚本")
                        }
                        items(normalScripts.size) { index ->
                            val script = normalScripts[index]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 8.dp),
                                insideMargin = PaddingValues(14.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ScriptIcon(script.icon)
                                    Spacer(Modifier.width(10.dp))
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { editing = script },
                                    ) {
                                        Text(script.name, fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface)
                                        Spacer(Modifier.height(2.dp))
                                        // 副标题优先用元数据 @description，没有再回退代码预览
                                        val subtitle = UserscriptEngine.parse(script.code).description
                                            .ifBlank { script.code.take(60).replace("\n", " ") }
                                        Text(
                                            text = subtitle,
                                            fontSize = 11.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            maxLines = 1,
                                        )
                                    }
                                    Switch(
                                        checked = script.enabled,
                                        onCheckedChange = { store.setEnabled(script.id, it) },
                                    )
                                    if (script.sourceUrl.isNotBlank()) {
                                        IconButton(onClick = {
                                            if (!busy) {
                                                busy = true
                                                scope.launch {
                                                    val fresh = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                        fetchScript(script.sourceUrl)
                                                    }
                                                    if (fresh != null) {
                                                        store.update(script.id, script.name, fresh, script.enabled, script.match)
                                                        android.widget.Toast.makeText(context, "已更新 ${script.name}", android.widget.Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        android.widget.Toast.makeText(context, "更新失败", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                    busy = false
                                                }
                                            }
                                        }) {
                                            Icon(imageVector = MiuixIcons.Glass.Refresh, contentDescription = "更新")
                                        }
                                    }
                                    IconButton(onClick = { deleting = script }) {
                                        Icon(imageVector = MiuixIcons.Glass.Delete, contentDescription = "删除")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 右下圆形添加按钮
            LiquidGlassFab(
                onClick = { creating = true },
                backdrop = liquid,
                primary = true,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(20.dp),
            ) {
                Icon(
                    imageVector = MiuixIcons.Glass.Add,
                    contentDescription = "新建脚本",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        } // end outer Box

        GlassDialog(
            show = deleteTarget != null,
            title = "删除脚本",
            summary = deleteTarget?.name,
            backdrop = backdrop,
            onDismissRequest = { deleting = null },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassDialogButton(
                    onClick = { deleting = null },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "取消", style = MiuixTheme.textStyles.button)
                }
                GlassDialogButton(
                    onClick = {
                        deleteTarget?.let { store.remove(it.id) }
                        deleting = null
                    },
                    modifier = Modifier.weight(1f),
                    role = GlassDialogButtonRole.Destructive,
                ) {
                    Text(text = "删除", style = MiuixTheme.textStyles.button)
                }
            }
        }

        ImportDialog(
            show = importing,
            backdrop = backdrop,
            onDismiss = { importing = false },
            onImport = { url ->
                importing = false
                busy = true
                scope.launch {
                    val code = withContext(kotlinx.coroutines.Dispatchers.IO) { fetchScript(url) }
                    if (code != null) {
                        if (AdParser.looksLikeFilterList(code)) {
                            // 这是去广告规则而不是脚本：交给「设置 → 高级 → 去广告」
                            val result = AdBlock.subscribe(url)
                            val msg = result.fold(
                                onSuccess = { "这是去广告规则，已加入「设置 → 高级 → 去广告」" },
                                onFailure = { it.message ?: "订阅失败" },
                            )
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            store.add("", code, sourceUrl = url)
                            android.widget.Toast.makeText(context, "已导入脚本", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        android.widget.Toast.makeText(context, "下载失败，检查链接", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    busy = false
                }
            },
        )

        ScriptDialog(
            show = creating || editing != null,
            initial = editing,
            backdrop = backdrop,
            onDismiss = {
                creating = false
                editing = null
            },
            onConfirmMatch = { name, code, enabled, match ->
                val current = editing
                if (current == null) {
                    store.add(name, code, match, "")
                } else {
                    store.update(current.id, name, code, enabled, match)
                }
                creating = false
                editing = null
            },
        )
    }
    // “更多”菜单：与 Scaffold 同级、放在最后 → 位于最上层（盖住顶栏）；
    // GlassPopup 自带全屏遮罩层，点菜单以外任何区域（含顶栏）都会触发 onDismissRequest 收起。
    // 锚点：面板顶边直接对齐按钮底部，右边与按钮右边对齐。
    GlassPopup(
        show = showMore,
        onDismissRequest = { showMore = false },
        anchorBounds = Rect(moreAnchor.left, moreAnchor.bottom, moreAnchor.right, moreAnchor.bottom),
        backdrop = if (glass) backdrop else null,
    ) {
        GlassPopupItem(
            text = "导入",
            onClick = {
                showMore = false
                importing = true
            },
            icon = MiuixIcons.Glass.Download,
        )
        GlassPopupItem(
            text = if (updatingAll) "更新中…" else "全部更新",
            enabled = !updatingAll,
            onClick = {
                showMore = false
                updateAll()
            },
            icon = MiuixIcons.Glass.Refresh,
        )
    }
    }
}

@Composable
private fun ScriptDialog(
    show: Boolean,
    initial: UserScript?,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    onDismiss: () -> Unit,
    onConfirmMatch: (String, String, Boolean, String) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var code by remember(initial) { mutableStateOf(initial?.code ?: "") }
    var match by remember(initial) { mutableStateOf(initial?.match ?: "") }
    var enabled by remember(initial) { mutableStateOf(initial?.enabled ?: true) }

    GlassDialog(
        show = show,
        title = if (initial == null) "新建脚本" else "编辑脚本",
        summary = "JavaScript 会在页面加载完成后执行",
        backdrop = backdrop,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            TextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = "名称",
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                value = match,
                onValueChange = { match = it },
                modifier = Modifier.fillMaxWidth(),
                label = "匹配（可留空，如 *://*.example.com/*）",
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                label = "JavaScript",
                maxLines = 8,
            )
            if (initial != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassDialogButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(text = "取消", style = MiuixTheme.textStyles.button)
                }
                GlassDialogButton(
                    onClick = { if (code.isNotBlank()) onConfirmMatch(name, code, enabled, match) },
                    modifier = Modifier.weight(1f),
                    role = GlassDialogButtonRole.Primary,
                ) {
                    Text(text = "保存", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}

@Composable
private fun ImportDialog(
    show: Boolean,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    GlassDialog(
        show = show,
        title = "从 URL 导入脚本",
        backdrop = backdrop,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = "脚本地址（.user.js）",
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassDialogButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(text = "取消", style = MiuixTheme.textStyles.button)
                }
                GlassDialogButton(
                    onClick = { if (url.isNotBlank()) onImport(url.trim()) },
                    modifier = Modifier.weight(1f),
                    role = GlassDialogButtonRole.Primary,
                ) {
                    Text(text = "导入", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}

private fun fetchScript(url: String): String? = runCatching {
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

@Composable
private fun BuiltinScriptCard(script: BuiltinScript, onAdd: (BuiltinScript) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp),
        insideMargin = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Glass.File,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(script.name, fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = script.description,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                text = "添加",
                onClick = { onAdd(script) },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                minHeight = 30.dp,
            )
        }
    }
}

@Composable
private fun ScriptIcon(url: String) {
    val icon = rememberRemoteIcon(url)
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = MiuixIcons.Glass.File,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
