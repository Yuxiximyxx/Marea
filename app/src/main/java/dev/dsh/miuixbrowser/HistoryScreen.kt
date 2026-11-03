package dev.dsh.miuixbrowser

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassPopup
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassTabRow
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.File
import top.yukonga.miuix.kmp.icon.glass.Download
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAB_BOOKMARKS = 0
private const val TAB_HISTORY = 1

/**
 * 书签 + 历史库：顶部分两栏（可左右滑动），书签按分组展示；
 * 书签栏右下角 FAB 提供新建 / 导入 / 导出（兼容 Chrome、Firefox 的 Netscape 书签 HTML）。
 */
@Composable
fun LibraryScreen(
    favorites: FavoritesStore,
    store: HistoryStore,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    initialTab: Int = TAB_HISTORY,
    blurEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val glass = rememberGlassActive(blurEnabled)
    val pagerState = rememberPagerState(
        initialPage = initialTab.coerceIn(0, 1),
        pageCount = { 2 },
    )
    val scrollBehavior = MiuixScrollBehavior()
    // 分页内容各自的滚动状态，避免两栏互相干扰
    val bookmarkListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val historyListState = androidx.compose.foundation.lazy.rememberLazyListState()

    var menuAnchor by remember { mutableStateOf(Rect.Zero) }
    var showMenu by remember { mutableStateOf(false) }
    var editingFav by remember { mutableStateOf<Favorite?>(null) }
    var creatingFav by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val html = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().decodeToString()
            } ?: return@rememberLauncherForActivityResult
            val n = favorites.importNetscapeHtml(html)
            Toast.makeText(
                context,
                if (n > 0) "已导入 $n 条书签" else "未识别到书签（支持 Chrome/Firefox 导出的 HTML）",
                Toast.LENGTH_SHORT,
            ).show()
        }.onFailure {
            Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageGlassTopBar(
                title = if (pagerState.currentPage == TAB_BOOKMARKS) "书签" else "历史",
                backdrop = backdrop,
                glass = glass,
                scrollBehavior = scrollBehavior,
                onBack = onBack,
                actions = {
                    if (pagerState.currentPage == TAB_HISTORY && store.items.isNotEmpty()) {
                        IconTextAction(
                            icon = MiuixIcons.Glass.Delete,
                            label = "清空",
                            modifier = Modifier.padding(end = 8.dp),
                        ) { store.clear() }
                    }
                },
                bottomContent = {
                    GlassTabRow(
                        tabs = listOf("书签", "历史"),
                        selectedIndex = pagerState.currentPage,
                        onSelect = { index ->
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        backdrop = if (glass) backdrop else null,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                },
            )
        },
    ) { innerPadding ->
        val topInset = innerPadding.calculateTopPadding()
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(MiuixTheme.colorScheme.surface),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true,
                    contentPadding = PaddingValues(top = topInset),
                ) { page ->
                    when (page) {
                        TAB_BOOKMARKS -> BookmarksPage(
                            favorites = favorites,
                            listState = bookmarkListState,
                            scrollBehavior = scrollBehavior,
                            onOpen = onOpen,
                            onEdit = { editingFav = it },
                        )

                        else -> HistoryPage(
                            store = store,
                            listState = historyListState,
                            scrollBehavior = scrollBehavior,
                            onOpen = onOpen,
                        )
                    }
                }
            }

            // 书签栏右下角 FAB：GlassPopup 放在全屏 Box 根坐标系，锚点用 boundsInRoot
            if (pagerState.currentPage == TAB_BOOKMARKS) {
                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 20.dp, bottom = 96.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primary)
                            .onGloballyPositioned { menuAnchor = it.boundsInRoot() }
                            .clickable {
                                haptic.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
                                )
                                showMenu = true
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Glass.Add,
                            contentDescription = "书签操作",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                // popup 与 FAB 同级、同在全屏 Box 内，坐标一致
                GlassPopup(
                    show = showMenu,
                    onDismissRequest = { showMenu = false },
                    anchorBounds = menuAnchor,
                    backdrop = backdrop,
                ) {
                    GlassPopupItem(
                        text = "新建书签",
                        onClick = {
                            showMenu = false
                            creatingFav = true
                        },
                        icon = MiuixIcons.Glass.Add,
                    )
                    GlassPopupItem(
                        text = "导入",
                        summary = "Chrome / Firefox 书签 HTML",
                        onClick = {
                            showMenu = false
                            importLauncher.launch(arrayOf("text/html", "text/plain", "*/*"))
                        },
                        icon = MiuixIcons.Glass.Download,
                    )
                    GlassPopupItem(
                        text = "导出",
                        summary = "保存到 Download/mafari",
                        onClick = {
                            showMenu = false
                            exportBookmarks(context, favorites.exportNetscapeHtml())
                        },
                        icon = MiuixIcons.Glass.File,
                    )
                }
            }
        }

        FavoriteEditorDialog(
            show = creatingFav || editingFav != null,
            initial = editingFav,
            backdrop = backdrop,
            onDismiss = {
                creatingFav = false
                editingFav = null
            },
            onDelete = editingFav?.let { fav ->
                {
                    val idx = favorites.items.indexOfFirst {
                        it.url == fav.url && it.name == fav.name
                    }
                    if (idx >= 0) favorites.remove(idx)
                    creatingFav = false
                    editingFav = null
                }
            },
            onConfirm = { name, url ->
                if (editingFav != null) {
                    val idx = favorites.items.indexOfFirst {
                        it.url == editingFav!!.url && it.name == editingFav!!.name
                    }
                    if (idx >= 0) {
                        favorites.update(idx, name, url, editingFav!!.group)
                    } else {
                        favorites.add(name, url, editingFav!!.group)
                    }
                } else {
                    favorites.add(name, url)
                }
                creatingFav = false
                editingFav = null
            },
        )
    }
}

@Composable
private fun BookmarksPage(
    favorites: FavoritesStore,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
    onOpen: (String) -> Unit,
    onEdit: (Favorite) -> Unit,
) {
    val groups = favorites.groups()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        if (favorites.items.isEmpty()) {
            item {
                Text(
                    text = "还没有书签。点右下角按钮新建，或从 Chrome / Firefox 导入。",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        groups.forEach { group ->
            val list = favorites.itemsIn(group)
            if (list.isEmpty()) return@forEach
            item(key = "g-$group") {
                SmallTitle(text = group)
            }
            items(list.size, key = { i -> "$group-${list[i].url}-$i" }) { i ->
                val fav = list[i]
                val haptic = LocalHapticFeedback.current
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
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpen(fav.url)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
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
                            Text(
                                text = fav.name,
                                fontSize = 15.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = fav.url,
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "编辑",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onEdit(fav) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryPage(
    store: HistoryStore,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
    onOpen: (String) -> Unit,
) {
    if (store.items.isEmpty()) {
        Text(
            text = "还没有浏览记录",
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(24.dp),
        )
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
    ) {
        item { SmallTitle(text = "最近访问") }
        items(store.items.size) { index ->
            val entry = store.items[index]
            val haptic = LocalHapticFeedback.current
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
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpen(entry.url)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = entry.url,
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = formatHistoryTime(entry.time),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

private fun formatHistoryTime(time: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - time
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000} 小时前"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(time))
    }
}

/** 写入系统 Download/mafari 并提示路径。 */
private fun exportBookmarks(context: android.content.Context, html: String) {
    runCatching {
        val name = "mafari-bookmarks-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".html"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/html")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/mafari")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建文件")
        resolver.openOutputStream(uri)?.use { it.write(html.toByteArray()) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        Toast.makeText(context, "已导出到 Download/mafari/$name", Toast.LENGTH_LONG).show()
    }.onFailure {
        Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun FavoriteEditorDialog(
    show: Boolean,
    initial: Favorite?,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(initial, show) { mutableStateOf(initial?.name ?: "") }
    var url by remember(initial, show) { mutableStateOf(initial?.url ?: "") }
    top.yukonga.miuix.kmp.glass.GlassDialog(
        show = show,
        title = if (initial == null) "新建书签" else "编辑书签",
        backdrop = backdrop,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            top.yukonga.miuix.kmp.basic.TextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = "名称",
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            top.yukonga.miuix.kmp.basic.TextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = "网址",
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (initial != null && onDelete != null) {
                    top.yukonga.miuix.kmp.glass.GlassDialogButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        role = top.yukonga.miuix.kmp.glass.GlassDialogButtonRole.Destructive,
                    ) {
                        Text(text = "删除", style = MiuixTheme.textStyles.button)
                    }
                } else {
                    top.yukonga.miuix.kmp.glass.GlassDialogButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "取消", style = MiuixTheme.textStyles.button)
                    }
                }
                top.yukonga.miuix.kmp.glass.GlassDialogButton(
                    onClick = { if (url.isNotBlank()) onConfirm(name, url) },
                    modifier = Modifier.weight(1f),
                    role = top.yukonga.miuix.kmp.glass.GlassDialogButtonRole.Primary,
                ) {
                    Text(text = "保存", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}
