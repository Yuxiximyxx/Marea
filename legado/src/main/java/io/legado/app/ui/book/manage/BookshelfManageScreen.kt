package io.legado.app.ui.book.manage

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import top.yukonga.miuix.kmp.basic.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.constant.IntentAction
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.help.book.getExportFileName
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.tryParesExportFileName
import io.legado.app.service.ExportBookService
import io.legado.app.ui.book.info.GroupSelectSheet
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppFloatingActionButtonMenu
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.AppRadioButton
import io.legado.app.ui.widget.components.checkBox.AppCheckbox
import io.legado.app.ui.widget.components.FabMenuItem
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyColumn
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.log.AppLogSheet
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.reorderAccessibility
import io.legado.app.ui.widget.components.settingItem.TinyClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.utils.ACache
import io.legado.app.utils.FileDoc
import io.legado.app.utils.checkWrite
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.move
import io.legado.app.utils.startService
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.verificationField
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState


private data class BookshelfManageListState(
    override val items: List<Book> = emptyList(),
    override val selectedIds: Set<Any> = emptySet(),
    override val searchKey: String = "",
    override val isSearch: Boolean = false,
    override val isLoading: Boolean = false
) : ListUiState<Book>

@Composable
fun BookshelfManageRouteScreen(
    groupId: Long,
    onBackClick: () -> Unit,
    onOpenBookInfo: (name: String, author: String, bookUrl: String) -> Unit,
    viewModel: BookshelfManageScreenViewModel = koinViewModel()
) {
    LaunchedEffect(groupId) {
        viewModel.dispatch(BookshelfManageScreenIntent.Initialize(groupId))
    }
    BookshelfManageScreen(
        viewModel = viewModel,
        onBackClick = onBackClick,
        onOpenBookInfo = onOpenBookInfo
    )
}

@Composable
private fun BookshelfManageScreen(
    viewModel: BookshelfManageScreenViewModel,
    onBackClick: () -> Unit,
    onOpenBookInfo: (name: String, author: String, bookUrl: String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showGroupMenu by remember { mutableStateOf(false) }
    var showFilePickerSheet by remember { mutableStateOf(false) }
    var showDownloadAllConfirmDialog by remember { mutableStateOf(false) }
    var showBatchDownloadConfirmDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showExportSettings by remember { mutableStateOf(false) }
    var showExportFileNameDialog by remember { mutableStateOf(false) }
    var showCharsetDialog by remember { mutableStateOf(false) }
    var showLogSheet by remember { mutableStateOf(false) }
    var showGroupSelectSheet by remember { mutableStateOf(false) }
    var showDeleteBookConfirmDialog by remember { mutableStateOf(false) }
    var showCustomExportDialog by remember { mutableStateOf(false) }
    var pendingMoveGroupBookUrl by remember { mutableStateOf<String?>(null) }
    var groupPickerCurrentGroupId by remember { mutableLongStateOf(0L) }
    var moreMenuBookUrl by remember { mutableStateOf<String?>(null) }
    var pendingDeleteBookUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    // 下面两处待导出集合直接存实体而不是 url：跨分组选中的书不在当前列表里，按 url 回查会漏掉
    var pendingExportBooks by remember { mutableStateOf<List<Book>>(emptyList()) }
    var exportSheetBooks by remember { mutableStateOf<List<Book>>(emptyList()) }
    var customExportPath by remember { mutableStateOf("") }
    var customExportBook by remember { mutableStateOf<Book?>(null) }
    var customExportAllChapter by remember { mutableStateOf(false) }
    var customEpubScopeInput by remember { mutableStateOf("") }
    var customEpubScopeError by remember { mutableStateOf<String?>(null) }
    var customEpubSizeInput by remember { mutableStateOf("1") }
    var customEpisodeExportNameInput by remember { mutableStateOf(state.exportConfig.episodeExportFileName) }
    var exportFileNameInput by remember { mutableStateOf(state.exportConfig.bookExportFileName.orEmpty()) }
    var exportCharsetInput by remember { mutableStateOf(state.exportConfig.exportCharset) }
    var isSearchMode by remember { mutableStateOf(false) }
    var searchKey by remember { mutableStateOf("") }
    // 选中集合由 ViewModel 持有：切分组、进详情再返回都不清空，因此可以跨分组多选
    val selectedBookUrls: Set<String> = state.selectedBookUrls
    var deleteOriginalBookFile by remember { mutableStateOf(state.deleteBookOriginal) }
    val exportBookPathKey = remember { "exportBookPath" }
    val exportTypes = remember { arrayListOf("txt", "epub") }
    val commonCharsets = remember { listOf("UTF-8", "GBK", "GB2312", "GB18030", "Big5", "UTF-16") }
    val focusRequester = remember { FocusRequester() }
    val exportFolderText = stringResource(R.string.export_folder)
    val exportAllText = stringResource(R.string.export_all)
    val exportChapterIndexText = stringResource(R.string.export_chapter_index)
    val fileContainsNumberText = stringResource(R.string.file_contains_number)
    val exportFileNameText = stringResource(R.string.export_file_name)
    val resultAnalyzedText = stringResource(R.string.result_analyzed)
    val errorScopeInputText = stringResource(R.string.error_scope_input)
    val noGroupText = stringResource(R.string.no_group)
    val exportFileNameHintText = stringResource(R.string.export_file_name_template_hint)
    val exportFileNameHelpText = stringResource(R.string.export_file_name_template_help)
    val userGroups = remember(state.groupList) { state.groupList.filter { it.groupId > 0L } }

    val groupNameResolver: (Book) -> String = remember(userGroups, noGroupText) {
        { book ->
            if (book.group <= 0L) {
                noGroupText
            } else {
                val groups = userGroups.filter {
                    (book.group and it.groupId) > 0L
                }
                if (groups.isEmpty()) noGroupText
                else groups.joinToString("、") { it.groupName }
            }
        }
    }
    val filteredBooks = remember(state.books, searchKey, isSearchMode, groupNameResolver) {
        if (!isSearchMode || searchKey.isBlank()) {
            state.books
        } else {
            val key = searchKey.trim()
            state.books.filter { book ->
                book.name.contains(key, true) ||
                        book.getRealAuthor().contains(key, true) ||
                        book.originName.contains(key, true) ||
                        groupNameResolver(book).contains(key, true)
            }
        }
    }
    val listUiState = remember(filteredBooks, selectedBookUrls, searchKey, isSearchMode) {
        BookshelfManageListState(
            items = filteredBooks,
            selectedIds = selectedBookUrls.mapTo(linkedSetOf()) { it as Any },
            searchKey = searchKey,
            isSearch = isSearchMode,
            isLoading = false
        )
    }
    val inSelectionMode = selectedBookUrls.isNotEmpty()
    // 跨分组选中的书里，有多少不在当前（分组 + 搜索）列表里
    val selectedOutOfViewCount = remember(selectedBookUrls, filteredBooks) {
        val inViewCount = filteredBooks.count { selectedBookUrls.contains(it.bookUrl) }
        selectedBookUrls.size - inViewCount
    }
    // 跨分组时 selectedIds 可能多于当前列表条目，"已选/总数" 会失真，这里自己出文案
    val selectionTitle = if (selectedOutOfViewCount > 0) {
        "已选 ${selectedBookUrls.size} 本（$selectedOutOfViewCount 本不在当前列表）"
    } else {
        "已选 ${selectedBookUrls.size}/${filteredBooks.size}"
    }
    val hasLocalBookInDeleteTarget = remember(state.selectedBooks, pendingDeleteBookUrls) {
        state.selectedBooks.any { pendingDeleteBookUrls.contains(it.bookUrl) && it.isLocal }
    }
    val clearSelection = {
        viewModel.dispatch(BookshelfManageScreenIntent.ClearBookSelection)
    }
    val toggleBookSelection: (Book) -> Unit = { book ->
        viewModel.dispatch(BookshelfManageScreenIntent.ToggleBookSelection(book.bookUrl))
    }

    BackHandler(enabled = inSelectionMode) {
        clearSelection()
    }

    val exportDir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        var isReadyPath = false
        var dirPath = ""
        uri?.let {
            if (uri.isContentScheme()) {
                ACache.get().put(exportBookPathKey, uri.toString())
                dirPath = uri.toString()
                isReadyPath = true
            } else {
                uri.path?.let { path ->
                    ACache.get().put(exportBookPathKey, path)
                    dirPath = path
                    isReadyPath = true
                }
            }
        }
        if (!isReadyPath) return@rememberLauncherForActivityResult
        val pendingBooks = pendingExportBooks
        if (pendingBooks.isEmpty()) return@rememberLauncherForActivityResult
        if (pendingBooks.size == 1 && state.exportConfig.isCustomEpubExportEnabled) {
            customExportPath = dirPath
            customExportBook = pendingBooks.single()
            customExportAllChapter = false
            customEpubScopeInput = ""
            customEpubScopeError = null
            customEpubSizeInput = "1"
            customEpisodeExportNameInput = state.exportConfig.episodeExportFileName
            showCustomExportDialog = true
            return@rememberLauncherForActivityResult
        }
        pendingBooks.forEach { book ->
            startExport(context, dirPath, book, state.exportConfig.exportType)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BookshelfManageScreenEffect.ShowMessage -> context.toastOnUi(effect.message)
                is BookshelfManageScreenEffect.NotifyBookChanged -> Unit
                is BookshelfManageScreenEffect.OpenBookInfo -> {
                    onOpenBookInfo(effect.name, effect.author, effect.bookUrl)
                }
            }
        }
    }

    fun selectExportFolder(books: List<Book> = emptyList()) {
        pendingExportBooks = books
        showFilePickerSheet = true
    }

    fun startExportForBooks(books: List<Book>) {
        if (books.isEmpty()) return
        val path = ACache.get().getAsString(exportBookPathKey)
        if (path.isNullOrEmpty() || !FileDoc.fromDir(path).checkWrite()) {
            selectExportFolder(books)
        } else if (state.exportConfig.isCustomEpubExportEnabled && books.size == 1) {
            customExportPath = path
            customExportBook = books.single()
            customExportAllChapter = false
            customEpubScopeInput = ""
            customEpubScopeError = null
            customEpubSizeInput = "1"
            customEpisodeExportNameInput = state.exportConfig.episodeExportFileName
            showCustomExportDialog = true
        } else {
            books.forEach { book ->
                startExport(context, path, book, state.exportConfig.exportType)
            }
        }
    }

    fun showExportSheetFor(books: List<Book>) {
        exportSheetBooks = books
        showExportSettings = false
        showExportSheet = exportSheetBooks.isNotEmpty()
    }

    fun exportAll() {
        showExportSheetFor(state.books)
    }

    fun exportSelected() {
        showExportSheetFor(state.selectedBooks)
    }
    fun resolveSelectionGroupMask(): Long {
        val targetBooks = state.selectedBooks
        if (targetBooks.isEmpty()) return 0L
        val firstGroup = targetBooks.first().group.coerceAtLeast(0L)
        return if (targetBooks.all { it.group == firstGroup }) firstGroup else 0L
    }
    val fabItems = listOf(
        FabMenuItem(
            Icons.Default.SelectAll,
            stringResource(R.string.select_all)
        ) {
            viewModel.dispatch(
                BookshelfManageScreenIntent.SelectVisibleBooks(
                    filteredBooks.mapTo(hashSetOf()) { it.bookUrl }
                )
            )
        },
        FabMenuItem(
            Icons.Default.Refresh,
            stringResource(R.string.revert_selection)
        ) {
            viewModel.dispatch(
                BookshelfManageScreenIntent.InvertVisibleBooks(
                    filteredBooks.mapTo(hashSetOf()) { it.bookUrl }
                )
            )
        },
        FabMenuItem(
            Icons.Default.Download,
            "缓存选中"
        ) {
            if (selectedBookUrls.isNotEmpty()) {
                showBatchDownloadConfirmDialog = true
            }
        },
        FabMenuItem(
            Icons.Default.Bookmarks,
            stringResource(R.string.move_to_group)
        ) {
            if (selectedBookUrls.isNotEmpty()) {
                groupPickerCurrentGroupId = resolveSelectionGroupMask()
                pendingMoveGroupBookUrl = null
                showGroupSelectSheet = true
            }
        },
        FabMenuItem(
            Icons.Default.Upload,
            "导出选中"
        ) {
            exportSelected()
        },
        FabMenuItem(
            Icons.Default.Delete,
            stringResource(R.string.clear_cache)
        ) {
            viewModel.dispatch(BookshelfManageScreenIntent.ClearCachesForBooks(selectedBookUrls))
            clearSelection()
        },
        FabMenuItem(
            Icons.Default.Delete,
            stringResource(R.string.delete)
        ) {
            if (selectedBookUrls.isNotEmpty()) {
                pendingDeleteBookUrls = selectedBookUrls
            deleteOriginalBookFile = state.deleteBookOriginal
                showDeleteBookConfirmDialog = true
            }
        }
    )
    val listState = rememberLazyListState()
    val canReorderBooks = state.bookSort == 3 && !isSearchMode && selectedBookUrls.isEmpty()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (canReorderBooks) {
            viewModel.dispatch(
                BookshelfManageScreenIntent.MoveBookOrder(
                    fromIndex = from.index,
                    toIndex = to.index
                )
            )
        }
    }
    ListScaffold(
        title = state.groupName ?: stringResource(R.string.offline_cache),
        state = listUiState,
        selectionTitle = selectionTitle,
        // 跨分组多选：选中后还要能继续搜索、切分组去挑别的书，所以顶栏 actions 不跟着选择态收起
        keepActionsInSelection = true,
        onBackClick = onBackClick,
        onSearchToggle = { active ->
            isSearchMode = active
            if (!active) {
                searchKey = ""
            }
        },
        onClearSelection = clearSelection,
        onSearchQueryChange = { searchKey = it },
        searchPlaceholder = "筛选书名/作者/书源/分组",
        topBarActions = {
            if (state.groupList.isNotEmpty()) {
                TopBarActionButton(
                    onClick = { showGroupMenu = true },
                    imageVector = AppIcons.Filter,
                    contentDescription = stringResource(R.string.a11y_group_filter)
                )
                RoundDropdownMenu(
                    expanded = showGroupMenu,
                    onDismissRequest = { showGroupMenu = false }
                ) { dismiss ->
                    state.groupList.forEach { group ->
                        RoundDropdownMenuItem(
                            text = group.groupName,
                            isSelected = group.groupId == state.groupId,
                            onClick = {
                                dismiss()
                                viewModel.dispatch(BookshelfManageScreenIntent.ChangeGroup(group.groupId))
                            }
                        )
                    }
                }
            }
        },
        dropDownMenuContent = { dismiss ->
            RoundDropdownMenuItem(
                text = stringResource(R.string.download_all),
                onClick = {
                    dismiss()
                    if (state.isDownloadRunning) {
                        viewModel.dispatch(BookshelfManageScreenIntent.StopDownload)
                    } else {
                        showDownloadAllConfirmDialog = true
                    }
                }
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.export_all),
                onClick = { dismiss(); exportAll() }
            )
            PillDivider()
            RoundDropdownMenuItem(
                text = stringResource(R.string.log),
                onClick = {
                    dismiss()
                    showLogSheet = true
                }
            )
        },
        floatingActionButton = {
            AppFloatingActionButtonMenu(
                modifier = Modifier.offset(x = 16.dp, y = 16.dp),
                expanded = fabMenuExpanded,
                onExpandedChange = { fabMenuExpanded = it },
                items = fabItems,
                visible = true,
                focusRequester = focusRequester
            )
        }
    ) { paddingValues ->
        val renderVersion by rememberUpdatedState(state.cacheVersion)
        FastScrollLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(filteredBooks, key = { _, item -> item.bookUrl }) { index, book ->
                val cacheCount = remember(renderVersion, book.bookUrl) {
                    viewModel.getCacheCount(book.bookUrl) ?: 0
                }
                val isPreparingDownload = viewModel.isBookPreparingDownload(book.bookUrl)
                val isDownloadingInCacheModel = viewModel.isBookDownloading(book.bookUrl)
                val isDownloading = isPreparingDownload || isDownloadingInCacheModel
                val downloadFailureText = viewModel.getDownloadFailureMessage(book.bookUrl)?.let {
                    stringResource(R.string.cache_download_failed, it)
                }
                val isSelected = selectedBookUrls.contains(book.bookUrl)
                val isMiuix = ThemeResolver.isMiuixEngine(composeEngine)
                val animatedContainerColor by animateColorAsState(
                    targetValue = if (isSelected)
                        LegadoTheme.colorScheme.secondaryContainer
                    else
                        if (isMiuix) LegadoTheme.colorScheme.surfaceContainer else LegadoTheme.colorScheme.surfaceContainerLow,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "CardColor"
                )
                val exportMsg = remember(renderVersion, book.bookUrl) {
                    ExportBookService.exportMsg[book.bookUrl]
                }
                ReorderableItem(
                    state = reorderableState,
                    key = book.bookUrl,
                    enabled = canReorderBooks
                ) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .reorderAccessibility(
                                index = index,
                                itemCount = filteredBooks.size,
                                enabled = canReorderBooks,
                            ) { from, to ->
                                viewModel.dispatch(
                                    BookshelfManageScreenIntent.MoveBookOrder(from, to)
                                )
                            }
                            .then(
                                if (canReorderBooks) {
                                    Modifier.longPressDraggableHandle()
                                } else {
                                    Modifier
                                }
                            ),
                        onClick = { toggleBookSelection(book) },
                        onLongClick = { toggleBookSelection(book) },
                        containerColor = animatedContainerColor
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    AppText(
                                        text = book.name,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable {
                                                viewModel.dispatch(
                                                    BookshelfManageScreenIntent.OpenBookInfoPreview(
                                                        book,
                                                        true
                                                    )
                                                )
                                            }
                                            .padding(horizontal = 4.dp),
                                        style = LegadoTheme.typography.titleSmallEmphasized,
                                        maxLines = 1
                                    )
                                    AppText(
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                        text = book.getRealAuthor(),
                                        style = LegadoTheme.typography.bodySmall
                                    )
                                    AppText(
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                        text = "${groupNameResolver(book)} | ${book.originName.ifBlank { book.origin }}",
                                        style = LegadoTheme.typography.labelSmallEmphasized.copy(color = LegadoTheme.colorScheme.primary)
                                    )
                                    if (exportMsg != null) {
                                        AppText(text = exportMsg, modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                                TextCard(
                                    text = if (book.isLocal) {
                                        stringResource(R.string.local_book)
                                    } else {
                                        stringResource(
                                            R.string.download_count,
                                            cacheCount,
                                            book.totalChapterNum
                                        )
                                    }
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SmallTonalButton(
                                        onClick = {
                                            if (!book.isLocal) {
                                                viewModel.dispatch(BookshelfManageScreenIntent.ToggleBookDownload(book))
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        icon = if (isDownloading) Icons.Default.Stop else Icons.Default.Download,
                                        text = if (isDownloading) "停止" else "下载",
                                        contentColor = LegadoTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.8f
                                        )
                                    )
                                    VerticalDivider(
                                        modifier = Modifier
                                            .height(16.dp)
                                            .padding(horizontal = 4.dp),
                                        color = LegadoTheme.colorScheme.outlineVariant
                                    )
                                    SmallTonalButton(
                                        onClick = { showExportSheetFor(listOf(book)) },
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Default.Upload,
                                        text = "导出",
                                        contentColor = LegadoTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.8f
                                        )
                                    )
                                    VerticalDivider(
                                        modifier = Modifier
                                            .height(16.dp)
                                            .padding(horizontal = 4.dp),
                                        color = LegadoTheme.colorScheme.outlineVariant
                                    )
                                    SmallTonalButton(
                                        onClick = {
                                            pendingMoveGroupBookUrl = book.bookUrl
                                            groupPickerCurrentGroupId = book.group.coerceAtLeast(0L)
                                            showGroupSelectSheet = true
                                        },
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Default.Bookmarks,
                                        text = "分组",
                                        contentColor = LegadoTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.8f
                                        )
                                    )
                                    VerticalDivider(
                                        modifier = Modifier
                                            .height(16.dp)
                                            .padding(horizontal = 4.dp),
                                        color = LegadoTheme.colorScheme.outlineVariant
                                    )
                                    RoundDropdownMenu(
                                        expanded = moreMenuBookUrl == book.bookUrl,
                                        onDismissRequest = { moreMenuBookUrl = null }
                                    ) { dismiss ->
                                        RoundDropdownMenuItem(
                                            text = "删除书籍",
                                            onClick = {
                                                pendingDeleteBookUrls = setOf(book.bookUrl)
                                                deleteOriginalBookFile = state.deleteBookOriginal
                                                showDeleteBookConfirmDialog = true
                                                dismiss()
                                            }
                                        )
                                        RoundDropdownMenuItem(
                                            text = "删除缓存",
                                            onClick = {
                                                viewModel.dispatch(
                                                    BookshelfManageScreenIntent.ClearCachesForBooks(
                                                        setOf(book.bookUrl)
                                                    )
                                                )
                                                dismiss()
                                            }
                                        )
                                    }
                                    SmallTonalButton(
                                        onClick = { moreMenuBookUrl = book.bookUrl },
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Default.MoreVert,
                                        text = "更多",
                                        contentColor = LegadoTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.8f
                                        )
                                    )
                                }
                                if (downloadFailureText != null) {
                                    AppText(
                                        text = downloadFailureText,
                                        style = LegadoTheme.typography.labelSmall,
                                        color = LegadoTheme.colorScheme.error,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    FilePickerSheet(
        show = showFilePickerSheet,
        onDismissRequest = { showFilePickerSheet = false },
        title = exportFolderText,
        onSelectSysDir = {
            showFilePickerSheet = false
            exportDir.launch(null)
        }
    )

    AppAlertDialog(
        show = showBatchDownloadConfirmDialog,
        onDismissRequest = { showBatchDownloadConfirmDialog = false },
        title = stringResource(R.string.draw),
        text = stringResource(R.string.sure_cache_book),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            showBatchDownloadConfirmDialog = false
            viewModel.dispatch(
                BookshelfManageScreenIntent.DownloadBooks(
                    bookUrls = selectedBookUrls,
                    downloadAllChapters = false
                )
            )
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { showBatchDownloadConfirmDialog = false }
    )

    AppAlertDialog(
        show = showDeleteBookConfirmDialog,
        onDismissRequest = { showDeleteBookConfirmDialog = false },
        title = stringResource(R.string.draw),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppText(text = stringResource(R.string.sure_del))
                if (hasLocalBookInDeleteTarget) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        AppCheckbox(
                            checked = deleteOriginalBookFile,
                            onCheckedChange = { checked ->
                                deleteOriginalBookFile = checked
                            }
                        )
                        AppText(
                            text = stringResource(R.string.delete_book_file),
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
        },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            showDeleteBookConfirmDialog = false
            viewModel.dispatch(
                BookshelfManageScreenIntent.DeleteBooks(
                    bookUrls = pendingDeleteBookUrls,
                    deleteOriginal = deleteOriginalBookFile
                )
            )
            pendingDeleteBookUrls = emptySet()
            clearSelection()
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { showDeleteBookConfirmDialog = false }
    )

    GroupSelectSheet(
        show = showGroupSelectSheet,
        groups = userGroups,
        currentGroupId = groupPickerCurrentGroupId,
        onDismissRequest = { showGroupSelectSheet = false },
        onConfirm = { groupId ->
            val moveSet = pendingMoveGroupBookUrl?.let { setOf(it) } ?: selectedBookUrls
            val targetGroupId = groupId.coerceAtLeast(0L)
            viewModel.dispatch(
                BookshelfManageScreenIntent.MoveBooksToGroup(
                    bookUrls = moveSet,
                    groupId = targetGroupId
                )
            )
            pendingMoveGroupBookUrl = null
            groupPickerCurrentGroupId = 0L
            showGroupSelectSheet = false
            clearSelection()
        }
    )

    AppAlertDialog(
        show = showDownloadAllConfirmDialog,
        onDismissRequest = { showDownloadAllConfirmDialog = false },
        title = stringResource(R.string.draw),
        text = stringResource(R.string.sure_cache_book),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            showDownloadAllConfirmDialog = false
            viewModel.dispatch(
                BookshelfManageScreenIntent.StartDownloadForVisibleBooks(
                    books = state.books,
                    downloadAllChapters = true
                )
            )
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { showDownloadAllConfirmDialog = false }
    )

    AppModalBottomSheet(
        show = showExportSheet,
        onDismissRequest = {
            showExportSettings = false
            showExportSheet = false
        },
        title = if (showExportSettings) "导出设置" else stringResource(R.string.export),
        startAction = {
            MediumTonalButton(
                onClick = { showExportSettings = !showExportSettings },
                icon = if (showExportSettings) Icons.Default.Upload else Icons.Default.Settings,
                contentDescription = if (showExportSettings) stringResource(R.string.export) else "导出设置",
            )
        },
    ) {
        AnimatedContent(
            targetState = showExportSettings,
            transitionSpec = {
                fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
            },
            label = "ExportSheetPage",
        ) { settings ->
            if (settings) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        TinyDropdownSettingItem(
                            title = stringResource(R.string.export_type),
                            selectedValue = state.exportConfig.exportType.toString(),
                            displayEntries = exportTypes.toTypedArray(),
                            entryValues = exportTypes.indices.map(Int::toString).toTypedArray(),
                            onValueChange = { type ->
                                viewModel.dispatch(
                                    BookshelfManageScreenIntent.SetExportType(
                                        type.toInt()
                                    )
                                )
                            },
                        )
                    }
                    item {
                        TinyDropdownSettingItem(
                            title = stringResource(R.string.export_charset),
                            selectedValue = state.exportConfig.exportCharset,
                            displayEntries = commonCharsets.toTypedArray(),
                            entryValues = commonCharsets.toTypedArray(),
                            onValueChange = { charset ->
                                viewModel.dispatch(
                                    BookshelfManageScreenIntent.SetExportCharset(charset)
                                )
                            },
                        )
                    }
                    item {
                        TinyClickableSettingItem(
                            title = "自定义字符集",
                            description = state.exportConfig.exportCharset,
                            onClick = {
                                exportCharsetInput =
                                    state.exportConfig.exportCharset; showCharsetDialog = true
                            })
                    }
                    item {
                        TinyClickableSettingItem(
                            title = stringResource(R.string.export_folder),
                            description = ACache.get().getAsString(exportBookPathKey) ?: "未选择",
                            onClick = { selectExportFolder() })
                    }
                    item {
                        TinyClickableSettingItem(
                            title = stringResource(R.string.export_file_name),
                            description = state.exportConfig.bookExportFileName.orEmpty(),
                            onClick = {
                                exportFileNameInput =
                                    state.exportConfig.bookExportFileName.orEmpty(); showExportFileNameDialog =
                                true
                            })
                    }
                    item {
                        TinySwitchSettingItem(
                            title = "替换净化",
                            checked = state.exportConfig.exportUseReplace,
                            onCheckedChange = {
                                viewModel.dispatch(
                                    BookshelfManageScreenIntent.SetExportUseReplace(it)
                                )
                            })
                    }
                    item {
                        TinySwitchSettingItem(
                            title = "自定义导出",
                            checked = state.exportConfig.enableCustomExport,
                            onCheckedChange = {
                                viewModel.dispatch(
                                    BookshelfManageScreenIntent.SetEnableCustomExport(it)
                                )
                            })
                    }
                    item {
                        TinySwitchSettingItem(
                            title = "导出包含章节名",
                            checked = !state.exportConfig.exportNoChapterName,
                            onCheckedChange = {
                                viewModel.dispatch(
                                    BookshelfManageScreenIntent.SetExportNoChapterName(!it)
                                )
                            })
                    }
                    item {
                        TinySwitchSettingItem(
                            title = "导出到WebDav",
                            checked = state.exportConfig.exportToWebDav,
                            onCheckedChange = {
                                viewModel.dispatch(
                                    BookshelfManageScreenIntent.SetExportToWebDav(it)
                                )
                            })
                    }
                    item {
                        TinySwitchSettingItem(
                            title = "导出插图文件",
                            checked = state.exportConfig.exportPictureFile,
                            onCheckedChange = {
                                viewModel.dispatch(
                                    BookshelfManageScreenIntent.SetExportPictureFile(it)
                                )
                            })
                    }
                    item {
                        TinySwitchSettingItem(
                            title = "并行导出",
                            checked = state.exportConfig.parallelExportBook,
                            onCheckedChange = {
                                viewModel.dispatch(
                                    BookshelfManageScreenIntent.SetParallelExportBook(it)
                                )
                            })
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AppText(
                        text = "本次导出（${exportSheetBooks.size} 本）",
                        style = LegadoTheme.typography.titleSmallEmphasized,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(exportSheetBooks, key = { it.bookUrl }) { book ->
                            AppText(
                                text = book.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                    MediumTonalButton(
                        onClick = {
                            showExportSheet = false; startExportForBooks(exportSheetBooks)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        icon = Icons.Default.Upload,
                        text = stringResource(R.string.export),
                    )
                }
            }
        }
    }

    AppAlertDialog(
        show = showExportFileNameDialog,
        onDismissRequest = { showExportFileNameDialog = false },
        title = stringResource(R.string.export_file_name),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppText(text = exportFileNameHelpText)
                AppTextField(
                    value = exportFileNameInput,
                    onValueChange = { exportFileNameInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = exportFileNameText,
                    placeholder = { AppText(exportFileNameHintText) }
                )
            }
        },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            viewModel.dispatch(BookshelfManageScreenIntent.SetBookExportFileName(exportFileNameInput))
            showExportFileNameDialog = false
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { showExportFileNameDialog = false }
    )

    AppAlertDialog(
        show = showCharsetDialog,
        onDismissRequest = { showCharsetDialog = false },
        title = stringResource(R.string.set_charset),
        content = {
            AppTextField(
                value = exportCharsetInput,
                onValueChange = { exportCharsetInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.set_charset)
            )
        },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            viewModel.dispatch(
                BookshelfManageScreenIntent.SetExportCharset(
                    exportCharsetInput.ifBlank { "UTF-8" }
                )
            )
            showCharsetDialog = false
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { showCharsetDialog = false }
    )

    AppLogSheet(
        show = showLogSheet,
        onDismissRequest = { showLogSheet = false }
    )

    val currentCustomBook = customExportBook
    AppAlertDialog(
        show = showCustomExportDialog && currentCustomBook != null,
        onDismissRequest = {
            showCustomExportDialog = false
            customEpubScopeError = null
        },
        title = stringResource(R.string.select_section_export),
        content = {
            val episodeTemplateValid = customEpisodeExportNameInput.isNotBlank()
                && tryParesExportFileName(customEpisodeExportNameInput)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppRadioButton(
                        selected = customExportAllChapter,
                        onClick = { customExportAllChapter = true }
                    )
                    AppText(
                        text = exportAllText,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppRadioButton(
                        selected = !customExportAllChapter,
                        onClick = { customExportAllChapter = false }
                    )
                    AppText(
                        text = exportChapterIndexText,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                if (!customExportAllChapter) {
                    AppTextField(
                        value = customEpubScopeInput,
                        onValueChange = {
                            customEpubScopeInput = it
                            customEpubScopeError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = exportChapterIndexText,
                        placeholder = { AppText("1-5,8,10-18") },
                        supportingText = {
                            customEpubScopeError?.let { msg ->
                                AppText(text = msg)
                            }
                        },
                        isError = customEpubScopeError != null
                    )
                    AppTextField(
                        value = customEpubSizeInput,
                        onValueChange = { customEpubSizeInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = fileContainsNumberText
                    )
                    AppTextField(
                        value = customEpisodeExportNameInput,
                        onValueChange = { customEpisodeExportNameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = exportFileNameText,
                        placeholder = { AppText(exportFileNameHintText) }
                    )
                    if (episodeTemplateValid) {
                        AppText(
                            text = "$resultAnalyzedText: ${
                                currentCustomBook?.getExportFileName(
                                    "epub",
                                    1,
                                    customEpisodeExportNameInput
                                ).orEmpty()
                            }"
                        )
                    } else if (customEpisodeExportNameInput.isNotBlank()) {
                        AppText(text = "Error")
                    }
                }
            }
        },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            val book = customExportBook ?: return@AppAlertDialog
            if (customExportAllChapter) {
                context.startService<ExportBookService> {
                    action = IntentAction.start
                    putExtra("bookUrl", book.bookUrl)
                    putExtra("exportType", "epub")
                    putExtra("exportPath", customExportPath)
                }
                showCustomExportDialog = false
                return@AppAlertDialog
            }
            if (!verificationField(customEpubScopeInput)) {
                customEpubScopeError = errorScopeInputText
                return@AppAlertDialog
            }
            customEpubScopeError = null
            if (customEpisodeExportNameInput.isNotBlank() && tryParesExportFileName(
                    customEpisodeExportNameInput
                )
            ) {
                viewModel.dispatch(BookshelfManageScreenIntent.SetEpisodeExportFileName(customEpisodeExportNameInput))
            }
            val epubSize = customEpubSizeInput.toIntOrNull()?.coerceAtLeast(1) ?: 1
            context.startService<ExportBookService> {
                action = IntentAction.start
                putExtra("bookUrl", book.bookUrl)
                putExtra("exportType", "epub")
                putExtra("exportPath", customExportPath)
                putExtra("epubSize", epubSize)
                putExtra("epubScope", customEpubScopeInput)
            }
            showCustomExportDialog = false
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = {
            showCustomExportDialog = false
            customEpubScopeError = null
        }
    )
}

private fun startExport(
    context: android.content.Context,
    path: String,
    book: Book,
    exportTypeIndex: Int
) {
    val exportType = if (exportTypeIndex == 1) "epub" else "txt"
    context.startService<ExportBookService> {
        action = IntentAction.start
        putExtra("bookUrl", book.bookUrl)
        putExtra("exportType", exportType)
        putExtra("exportPath", path)
    }
}
