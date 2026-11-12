package io.legado.app.ui.main

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.domain.model.BookSearchScope
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.feature.reader.platform.ReaderPerfTrace
import io.legado.app.model.Download
import io.legado.app.model.ReadBook
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.about.AboutEffect
import io.legado.app.ui.about.AboutScreen
import io.legado.app.ui.about.AboutViewModel
import io.legado.app.ui.book.bookmark.AllBookmarkRouteScreen
import io.legado.app.ui.book.cache.manage.BookCacheManageRouteScreen
import io.legado.app.ui.book.import.local.ImportBookRouteScreen
import io.legado.app.ui.book.import.remote.RemoteBookRouteScreen
import io.legado.app.ui.book.info.BookInfoRouteScreen
import io.legado.app.ui.book.info.BookInfoViewModel
import io.legado.app.ui.book.info.edit.BookInfoEditScreen
import io.legado.app.ui.book.info.edit.BookInfoEditViewModel
import io.legado.app.ui.book.manage.BookshelfManageRouteScreen
import io.legado.app.ui.book.manga.MangaReaderIntent
import io.legado.app.ui.book.manga.MangaReaderRouteScreen
import io.legado.app.ui.book.manga.MangaReaderViewModel
import io.legado.app.ui.book.read.ReadBookController
import io.legado.app.ui.book.read.ReadBookInitRequest
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookRouteScreen
import io.legado.app.ui.book.read.ReadBookViewModel
import io.legado.app.ui.book.read.ReaderSessionViewModel
import io.legado.app.ui.book.readRecord.ReadRecordOverviewRouteScreen
import io.legado.app.ui.book.readRecord.ReadRecordRouteScreen
import io.legado.app.ui.book.searchContent.SearchContentRouteScreen
import io.legado.app.ui.book.searchContent.SearchContentViewModel
import io.legado.app.ui.book.toc.TocIntent
import io.legado.app.ui.book.toc.TocRouteScreen
import io.legado.app.ui.book.toc.TocViewModel
import io.legado.app.ui.book.toc.rule.TxtRuleRouteScreen
import io.legado.app.ui.book.toc.rule.preview.TxtTocRulePreviewRouteScreen
import io.legado.app.ui.config.ConfigNavScreen
import io.legado.app.ui.theme.MiuixSurface
import io.legado.app.ui.config.backupConfig.BackupConfigRouteScreen
import io.legado.app.ui.config.coverConfig.CoverAlbumManageRouteScreen
import io.legado.app.ui.config.coverConfig.CoverConfigRouteScreen
import io.legado.app.ui.config.customTheme.CustomThemeRouteScreen
import io.legado.app.ui.config.downloadCacheConfig.DownloadCacheConfigRouteScreen
import io.legado.app.ui.config.labConfig.LabConfigRouteScreen
import io.legado.app.ui.config.otherConfig.OtherConfigRouteScreen
import io.legado.app.ui.config.privateConfig.PrivateConfigRouteScreen
import io.legado.app.ui.config.readConfig.ReadConfigRouteScreen
import io.legado.app.ui.config.themeConfig.ThemeConfigRouteScreen
import io.legado.app.ui.config.themeManage.ThemeManageRouteScreen
import io.legado.app.ui.config.translation.TranslationConfigRouteScreen
import io.legado.app.ui.dict.rule.DictRuleRouteScreen
import io.legado.app.ui.highlightTagRule.HighlightTagRuleRouteScreen
import io.legado.app.ui.replace.ReplaceRuleRouteScreen
import io.legado.app.ui.replace.edit.ReplaceEditRouteScreen
import io.legado.app.ui.replace.edit.ReplaceEditViewModel
import io.legado.app.ui.widget.components.privacy.PrivateReadGate
import io.legado.app.ui.widget.components.privacy.PrivateVerifyGate
import io.legado.app.utils.openUrl
import io.legado.app.utils.sendToClip
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.toggleSystemBar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** 全屏书页（阅读/漫画）走普通 nav3 场景：与文本阅读页同一段 600ms 交叉淡入淡出。 */
private fun readerEntryMetadata(predictiveBackEnabled: Boolean) = metadata {
    put(NavDisplay.TransitionKey) {
        fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
    }
    put(NavDisplay.PopTransitionKey) {
        fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
    }
    if (predictiveBackEnabled) {
        put(NavDisplay.PredictivePopTransitionKey) { _ ->
            fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
        }
    }
}

/** 详情页更快一档：它是「打开一本书」的第二段，下面还压着阅读页。 */
private fun bookInfoEntryMetadata(predictiveBackEnabled: Boolean) = metadata {
    put(NavDisplay.TransitionKey) {
        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
    }
    put(NavDisplay.PopTransitionKey) {
        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
    }
    if (predictiveBackEnabled) {
        put(NavDisplay.PredictivePopTransitionKey) { _ ->
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        }
    }
}

/** Keep parent overlays composed while NavDisplay leaves animation to the search scene or player host. */
private fun modalOverlayEntryMetadata(): Map<String, Any> =
    ModalOverlaySceneStrategy.modalOverlay() + metadata {
        put(NavDisplay.TransitionKey) { EnterTransition.None togetherWith ExitTransition.None }
        put(NavDisplay.PopTransitionKey) { EnterTransition.None togetherWith ExitTransition.None }
        put(NavDisplay.PredictivePopTransitionKey) { _ ->
            EnterTransition.None togetherWith ExitTransition.None
        }
    }

/**
 * 听书播放弹层「经典控制」的目标判定：栈顶是阅读界面时把请求交给它，
 * 否则新开阅读界面。抽成纯函数以便单测覆盖这条分支。
 *
 * 播放弹层不在导航栈上，所以判据是「当前顶层」而不是「上一站」。
 */
internal fun isReaderOnTop(backStack: List<NavKey>): Boolean =
    backStack.lastOrNull() is MainRouteReadBook

@Composable
private fun ConsumeBookPageResult(
    route: NavKey,
    isTop: Boolean,
    tracker: MainNavRouteTracker,
    onResult: suspend (BookPageResult) -> Unit,
) {
    val results by tracker.bookPageResults.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val result = results[route]
    LaunchedEffect(route, isTop, result, lifecycleOwner) {
        if (isTop && result != null) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (tracker.currentRoute == route && tracker.bookPageResults.value[route] == result) {
                    onResult(result)
                    tracker.takeBookPageResult(route)
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun MainActivity.mainEntryProvider(
    backStack: MutableList<NavKey>,
    configuration: AppUiConfiguration,
    showMangaUi: Boolean,
    useRail: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    navigateToRoute: (NavKey) -> Unit,
    panesEnabled: Boolean,
    onNavigationRailWidthChanged: (androidx.compose.ui.unit.Dp) -> Unit,
    onNavigateBack: () -> Unit,
    /**
     * 「胶囊 ↔ 听书播放页」的形变状态。
     *
     * 主界面要把听书胶囊排在悬浮底栏同一行里，需要把胶囊的锚点上报给同一个进度源，
     * 所以这里透传（其余目的地不用）。
     */
) = mainPaneEntryProvider(backStack, panesEnabled, navRouteTracker, navigateToRoute) {
    entry<MainRouteToc> { route ->
        // VM 提到 entry 里，是为了让「规则列表选好的规则」能从这里回灌（SaveTocRegex）。
        val tocViewModel = koinViewModel<TocViewModel>()
        ConsumeBookPageResult(route, backStack.lastOrNull() == route, navRouteTracker) { result ->
            when (result) {
                is BookPageResult.TxtTocRuleChosen -> {
                    tocViewModel.onIntent(TocIntent.SaveTocRegex(result.rule))
                }

                else -> Unit
            }
        }
        TocRouteScreen(
            viewModel = tocViewModel,
            bookUrl = route.bookUrl,
            initialPage = route.initialPage,
            onBackClick = { closeBookPage(backStack, route) },
            onChapterClick = { index ->
                closeBookPage(backStack, route, BookPageResult.ChapterSelected(index, 0))
            },
            onBookmarkClick = { index, position ->
                closeBookPage(backStack, route, BookPageResult.ChapterSelected(index, position))
            },
            onOpenReplaceRule = { editor ->
                onNavigateToRoute(editor?.let { MainRouteReplaceEdit(it) } ?: MainRouteReplaceRules(
                    route.bookUrl
                ))
            },
            onPickLocalTocRule = { regex ->
                onNavigateToRoute(
                    MainRouteTxtTocRule(
                        initialRule = regex,
                        pickForBookUrl = route.bookUrl,
                    )
                )
            },
        )
    }
    entry<MainRouteBookInfoEdit> { route ->
        val viewModel = koinViewModel<BookInfoEditViewModel>()
        LaunchedEffect(route.bookUrl) { viewModel.loadBook(route.bookUrl) }
        BookInfoEditScreen(
            viewModel = viewModel,
            onBack = { closeBookPage(backStack, route) },
            onSave = {
                viewModel.save {
                    closeBookPage(
                        backStack,
                        route,
                        BookPageResult.InfoEdited
                    )
                }
            },
        )
    }
    entry<MainRouteReplaceRules> { route ->
        ReplaceRuleRouteScreen(
            bookUrl = route.bookUrl,
            onBackClick = { closeBookPage(backStack, route) },
            onNavigateToEdit = { onNavigateToRoute(MainRouteReplaceEdit(it)) },
        )
    }
    entry<MainRouteReplaceEdit> { route ->
        val viewModel =
            koinViewModel<ReplaceEditViewModel>(key = "replace_edit_${route.editor.sessionId}") {
                parametersOf(route.editor)
            }
        ReplaceEditRouteScreen(
            viewModel = viewModel,
            onBack = { closeBookPage(backStack, route) },
            onSaveSuccess = { closeBookPage(backStack, route) },
        )
    }
    entry<MainRouteHome> {
        val mainViewModel = koinViewModel<MainViewModel>()
        val mainUiState by mainViewModel.uiState.collectAsStateWithLifecycle()
        MainScreen(
            mainUiState = mainUiState,
            onIntent = mainViewModel::onIntent,
            effects = mainViewModel.effects,
            useRail = useRail,
            onNavigationRailWidthChanged = onNavigationRailWidthChanged,
            onMainDestinationChanged = {
                if (panesEnabled) onNavigateToRoute(MainRouteHome)
            },
            onOpenSettings = {
                onNavigateToRoute(MainRouteSettings)
            },
            onNavigateToRemoteImport = {
                onNavigateToRoute(MainRouteImportRemote)
            },
            onNavigateToLocalImport = {
                onNavigateToRoute(MainRouteImportLocal)
            },
            onNavigateToCache = { groupId ->
                onNavigateToRoute(MainRouteCache(groupId))
            },
            onNavigateToBookCacheManage = {
                onNavigateToRoute(MainRouteBookCacheManage)
            },
            onOpenBookshelfBook = { book, sharedCoverKey ->
                if (!book.isLocal && book.isImage && showMangaUi) {
                    onNavigateToRoute(
                        MainRouteReadManga(
                            bookUrl = book.bookUrl,
                            sharedCoverKey = sharedCoverKey
                        )
                    )
                } else {
                    onNavigateToRoute(
                        MainRouteReadBook(
                            bookUrl = book.bookUrl,
                            sharedCoverKey = sharedCoverKey,
                        )
                    )
                }
            },
            onNavigateToBackupSettings = {
                onNavigateToRoute(MainRouteSettingsBackup)
            },
            onNavigateToBookInfo = { name, author, bookUrl, origin, coverPath, sharedCoverKey ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name = name,
                        author = author,
                        bookUrl = bookUrl,
                        origin = origin,
                        coverPath = coverPath,
                        sharedCoverKey = sharedCoverKey
                    )
                )
            },
            onNavigateToReplaceRules = { onNavigateToRoute(MainRouteReplaceRules()) },
            onNavigateToReadRecord = {
                onNavigateToRoute(MainRouteReadRecord)
            },
            onNavigateToReadRecordOverview = {
                onNavigateToRoute(MainRouteReadRecordOverview)
            },
            onNavigateToHighlightTagRule = {
                onNavigateToRoute(MainRouteHighlightTagRule)
            },
            onNavigateToDictRule = {
                onNavigateToRoute(MainRouteDictRule)
            },
            onNavigateToTxtTocRule = {
                onNavigateToRoute(MainRouteTxtTocRule())
            },
            onNavigateToAllBookmark = {
                onNavigateToRoute(MainRouteAllBookmark)
            },
            onNavigateToAbout = {
                onNavigateToRoute(MainRouteAbout)
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }

    entry<MainRouteSettings> {
        // exp.12：设置主界面已去 MD3、只用 Miuix 组件，根部补 Miuix 表面环境
        MiuixSurface {
                ConfigNavScreen(
                    onBackClick = { onNavigateBack() },
                    onNavigateToOther = { pushRoute(MainRouteSettingsOther) },
                    onNavigateToRead = { pushRoute(MainRouteSettingsRead) },
                    // 朗读规则 hub：入口从「我的」移到设置，页面标题与这里统一叫「朗读设置」
                    onNavigateToCover = { pushRoute(MainRouteSettingsCover) },
                    onNavigateToTheme = { pushRoute(MainRouteSettingsTheme) },
                    onNavigateToBackup = { pushRoute(MainRouteSettingsBackup) },
                    onNavigateToDownloadCache = { pushRoute(MainRouteSettingsDownloadCache) },
                    onNavigateToTranslation = { pushRoute(MainRouteSettingsTranslation) },
                    onNavigateToLab = { pushRoute(MainRouteSettingsLabConfig) },
                    onNavigateToPrivate = { pushRoute(MainRouteSettingsPrivate) }
                )
        }
        }

    entry<MainRouteSettingsOther> {
        // exp.12：设置主界面已去 MD3、只用 Miuix 组件，根部补 Miuix 表面环境
        MiuixSurface {
                OtherConfigRouteScreen(onBackClick = { onNavigateBack() })
        }
        }

    entry<MainRouteSettingsRead> {
        // exp.12：设置主界面已去 MD3、只用 Miuix 组件，根部补 Miuix 表面环境
        MiuixSurface {
                ReadConfigRouteScreen(onBackClick = { onNavigateBack() })
        }
        }

    entry<MainRouteSettingsCover> {
        // exp.12：设置主界面已去 MD3、只用 Miuix 组件，根部补 Miuix 表面环境
        MiuixSurface {
                CoverConfigRouteScreen(
                    onBackClick = { onNavigateBack() },
                    onNavigateToCoverAlbums = {
                        pushRoute(MainRouteSettingsCoverAlbums)
                    },
                )
        }
        }

    entry<MainRouteSettingsCoverAlbums> {
        // exp.12：设置主界面已去 MD3、只用 Miuix 组件，根部补 Miuix 表面环境
        MiuixSurface {
                CoverAlbumManageRouteScreen(onBackClick = { onNavigateBack() })
        }
        }

    entry<MainRouteSettingsTheme> {
        // exp.12：设置主界面已去 MD3、只用 Miuix 组件，根部补 Miuix 表面环境
        MiuixSurface {
                ThemeConfigRouteScreen(
                    onBackClick = { onNavigateBack() },
                    onNavigateToCustomTheme = { pushRoute(MainRouteSettingsCustomTheme) },
                    onNavigateToThemeManage = { pushRoute(MainRouteSettingsThemeManage) }
                )
        }
        }

    entry<MainRouteSettingsBackup> {
        // exp.12：设置主界面已去 MD3、只用 Miuix 组件，根部补 Miuix 表面环境
        MiuixSurface {
                BackupConfigRouteScreen(onBackClick = { onNavigateBack() })
        }
        }







    entry<MainRouteSettingsDownloadCache> {
        // exp.12：设置主界面已去 MD3、只用 Miuix 组件，根部补 Miuix 表面环境
        MiuixSurface {
                DownloadCacheConfigRouteScreen(onBackClick = { onNavigateBack() })
        }
        }

    entry<MainRouteSettingsTranslation> {
        // exp.12：设置主界面已去 MD3、只用 Miuix 组件，根部补 Miuix 表面环境
        MiuixSurface {
                TranslationConfigRouteScreen(
                    onBackClick = { onNavigateBack() },
                )
        }
        }

    entry<MainRouteSettingsLabConfig> {
        // exp.12：设置主界面已去 MD3、只用 Miuix 组件，根部补 Miuix 表面环境
        MiuixSurface {
                LabConfigRouteScreen(onBackClick = { onNavigateBack() })
        }
        }

    entry<MainRouteSettingsPrivate> {
        // 隐私设置页可以关掉三种验证时机，所以进入前要求确认身份（生物或密码）。
        // 只校验、不授予：进来一次不该顺带解锁私密内容。
        PrivateVerifyGate(onCancel = { onNavigateBack() }) {
            // exp.12：同其它设置页，根部补 Miuix 表面环境
            MiuixSurface {
                PrivateConfigRouteScreen(onBackClick = { onNavigateBack() })
            }
        }
    }

    entry<MainRouteSettingsCustomTheme> {
        // exp.12：设置主界面已去 MD3、只用 Miuix 组件，根部补 Miuix 表面环境
        MiuixSurface {
                CustomThemeRouteScreen(
                    onBackClick = { onNavigateBack() }
                )
        }
        }

    entry<MainRouteSettingsThemeManage> {
        // exp.12：设置主界面已去 MD3、只用 Miuix 组件，根部补 Miuix 表面环境
        MiuixSurface {
                ThemeManageRouteScreen(onBackClick = { onNavigateBack() })
        }
        }

    entry<MainRouteImportLocal> {
        ImportBookRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteImportRemote> {
        RemoteBookRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteCache> { route ->
        // exp.13：书架管理页整页纳入 MiuixSurface（exp.12 只随共享组件层跟随）
        MiuixSurface {
            BookshelfManageRouteScreen(
                groupId = route.groupId,
                onBackClick = { onNavigateBack() },
                onOpenBookInfo = { name, author, bookUrl ->
                    onNavigateToRoute(
                        MainRouteBookInfo(
                            name = name,
                            author = author,
                            bookUrl = bookUrl
                        )
                    )
                }
            )
        }
    }

    entry<MainRouteBookCacheManage> {
        BookCacheManageRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteReadBook>(
        metadata = readerEntryMetadata(configuration.appShell.predictiveBackEnabled)
    ) { route ->
        // 私密闸门：阅读记录 / 通知栏 / 深链等直开阅读器的路径全都在这里被拦一次，
        // 未授权时阅读器根本不会被组合（也就没有加载正文的机会）
        PrivateReadGate(
            bookUrl = route.bookUrl,
            onExit = {
                if (backStack.size > 1) {
                    MainNavigator.navigateBack(
                        this@mainEntryProvider,
                        backStack,
                        navRouteTracker,
                        fromRoute = route,
                    )
                } else {
                    // 通知 / 深链可能让阅读器成为栈里唯一一项：没有"当前页"可退，
                    // 按约定落到书架，而不是把应用关掉
                    backStack.clear()
                    backStack.add(MainRouteHome)
                }
            },
            onOpenLocalPasswordSettings = {
                onNavigateToRoute(MainRouteSettingsPrivate)
            },
        ) {
            ReaderPerfTrace.marker("nav.entry.begin")
            val readBookViewModel = koinViewModel<ReadBookViewModel>(
                key = "ReadBook:${route.bookUrl ?: "last-read"}"
            )
            ReaderPerfTrace.marker("nav.book-vm.ready")
            val readerSessionViewModel = koinViewModel<ReaderSessionViewModel>(
                key = "ReaderSession:${route.bookUrl ?: "last-read"}"
            )
            ReaderPerfTrace.marker("nav.viewmodels.ready")
            val controller = remember(readBookViewModel, readerSessionViewModel) {
                ReadBookController(
                    this@mainEntryProvider,
                    readBookViewModel,
                    readerSessionViewModel,
                    // 画布在首次组合就会请求分页，本路由要开哪本书必须随构造就位：
                    // 那期间 ReadBook 单例里可能还是上一本书的章节。
                    routeBookUrl = route.bookUrl,
                )
            }
            ReaderPerfTrace.marker("nav.controller.ready")
            val lifecycleOwner = LocalLifecycleOwner.current
            val initRequest = remember(route) {
                ReadBookInitRequest(
                    bookUrl = route.bookUrl,
                    inBookshelf = route.inBookshelf,
                    chapterChanged = route.chapterChanged,
                )
            }
            val effectsReady = remember(readBookViewModel) { CompletableDeferred<Unit>() }
            ConsumeBookPageResult(
                route,
                backStack.lastOrNull() == route,
                navRouteTracker
            ) { result ->
                effectsReady.await()
                when (result) {
                    is BookPageResult.ChapterSelected -> readBookViewModel.onIntent(
                        ReadBookIntent.OpenChapterResult(result.index, result.position)
                    )

                    is BookPageResult.TxtTocRuleChosen -> readBookViewModel.onIntent(
                        ReadBookIntent.TocRegexResult(result.rule)
                    )

                    BookPageResult.RulesClosed -> readBookViewModel.onIntent(ReadBookIntent.ReplaceRuleResult)
                    else -> Unit
                }
            }
            val bookInfoResults by navRouteTracker.bookInfoResults.collectAsStateWithLifecycle()
            LaunchedEffect(bookInfoResults[route], backStack.lastOrNull()) {
                if (backStack.lastOrNull() == route && bookInfoResults.containsKey(route)) {
                    effectsReady.await()
                    navRouteTracker.takeBookInfoResult(route)?.let { deleted ->
                        readBookViewModel.onIntent(ReadBookIntent.BookInfoResult(deleted))
                    }
                }
            }
            val readerResumeState = remember(controller, lifecycleOwner) { booleanArrayOf(false) }
            val collectorReady = remember(readBookViewModel) { booleanArrayOf(false) }
            fun resumeReader() {
                if (readerResumeState[0]) return
                readerResumeState[0] = true
                controller.onResume()
                readBookViewModel.onIntent(ReadBookIntent.OnResume)
                // 回到阅读界面时把可见页对齐到当前朗读位置：朗读期间用户可能进听书页跳段/跳章，
                // 服务驱动的是内部页游标，可见页需要显式回位，否则停在离开时那一页。
                // 只在跟随状态下做（用户手动翻页脱离后不回拉），语义与「回到朗读位置」一致。
            }

            fun pauseReader() {
                if (!readerResumeState[0]) return
                readerResumeState[0] = false
                readBookViewModel.onIntent(ReadBookIntent.OnPause)
                controller.onPause()
            }

            ReadBookRouteScreen(
                viewModel = readBookViewModel,
                readerSessionViewModel = readerSessionViewModel,
                host = controller,
                controller = controller,
                sharedTransitionScope = sharedTransitionScope.takeIf {
                    configuration.appShell.readerSharedElementEnabled
                },
                animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                sharedCoverKey = route.sharedCoverKey,
                isTopRoute = (backStack.lastOrNull() as? MainRouteReadBook)?.let {
                    route.bookUrl == null || it.bookUrl == route.bookUrl
                } ?: false,
                onEffectsReady = { effectsReady.complete(Unit) },
                onOpenToc = { bookUrl, page -> onNavigateToRoute(MainRouteToc(bookUrl, page)) },
                onOpenTocRulePreview = { bookUrl, tocRegex ->
                    onNavigateToRoute(
                        MainRouteTxtTocRulePreview(bookUrl = bookUrl, currentTocRegex = tocRegex)
                    )
                },
                onOpenReplaceRule = { bookUrl, editor ->
                    onNavigateToRoute(editor?.let { MainRouteReplaceEdit(it) }
                        ?: MainRouteReplaceRules(bookUrl))
                },
                onOpenBookInfo = { name, author, bookUrl ->
                    onNavigateToRoute(
                        MainRouteBookInfo(
                            name, author, bookUrl,
                            openRequestId = System.nanoTime(),
                        )
                    )
                },
                onNavigateBack = {
                    MainNavigator.navigateBack(
                        this@mainEntryProvider,
                        backStack,
                        navRouteTracker,
                        fromRoute = route,
                    )
                },
            )

            DisposableEffect(controller, lifecycleOwner, route.readAloud) {
                activeReadBookInputHandler = controller
                activeReadBookRoute = route
                MainActivity.hasActiveReadBookRoute = true
                controller.onClose = { onNavigateBack() }

                val lifecycleObserver = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            if (collectorReady[0]) resumeReader()
                        }

                        Lifecycle.Event.ON_PAUSE -> pauseReader()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
                onDispose {
                    pauseReader()
                    readBookViewModel.onIntent(ReadBookIntent.OnDispose)
                    lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                    if (activeReadBookInputHandler === controller) {
                        activeReadBookInputHandler = null
                    }
                    if (activeReadBookRoute == route) {
                        activeReadBookRoute = null
                    }
                    MainActivity.hasActiveReadBookRoute = false
                    controller.onClose = null
                    controller.clearTts()
                    this@mainEntryProvider.toggleSystemBar(configuration.appShell.showStatusBar)
                }
            }

            LaunchedEffect(route, readBookViewModel, lifecycleOwner) {
                // Resolving the book and applying its read style do not depend on launcher effects.
                // Start that I/O immediately; initData still waits below because it can emit effects.
                val initialBook = readBookViewModel.initReadBookConfig(initRequest)
                effectsReady.await()
                collectorReady[0] = true
                readBookViewModel.initData(initRequest, initialBook) {
                    readBookViewModel.markJustInitData()
                    controller.onRouteInitialized()
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        resumeReader()
                    }
                }
            }
        }
    }

    entry<MainRouteReadManga>(
        metadata = readerEntryMetadata(configuration.appShell.predictiveBackEnabled)
    ) { route ->
        val mangaViewModel = koinViewModel<MangaReaderViewModel>(
            key = "ReadManga:${route.bookUrl ?: "last-read"}",
        )
        ConsumeBookPageResult(route, backStack.lastOrNull() == route, navRouteTracker) { result ->
            when (result) {
                is BookPageResult.ChapterSelected ->
                    mangaViewModel.onIntent(
                        MangaReaderIntent.OpenChapter(
                            result.index,
                            result.position
                        )
                    )

                BookPageResult.BookDeleted -> MainNavigator.navigateBack(
                    this@mainEntryProvider, backStack, navRouteTracker, fromRoute = route,
                )

                else -> Unit
            }
        }
        MangaReaderRouteScreen(
            bookUrl = route.bookUrl,
            inBookshelf = route.inBookshelf,
            chapterChanged = route.chapterChanged,
            openRequestId = route.openRequestId,
            viewModel = mangaViewModel,
            onOpenToc = { bookUrl, page -> onNavigateToRoute(MainRouteToc(bookUrl, page)) },
            restoreSystemBarsVisible = configuration.appShell.showStatusBar,
            sharedTransitionScope = sharedTransitionScope.takeIf {
                configuration.appShell.readerSharedElementEnabled
            },
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            sharedCoverKey = route.sharedCoverKey,
            isTopRoute = backStack.lastOrNull() == route,
            onFinish = {
                MainNavigator.navigateBack(
                    this@mainEntryProvider,
                    backStack,
                    navRouteTracker,
                    fromRoute = route,
                )
            },
            onOpenBookInfo = { name, author, bookUrl ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name, author, bookUrl,
                        openRequestId = System.nanoTime(),
                    )
                )
            },
        )
    }


    // 兼容已保存的导航栈。新入口在 MainActivity 直接打开同窗口播放浮层。

    entry<MainRouteSearchContent>(
        metadata = modalOverlayEntryMetadata() + ModalOverlaySceneStrategy.searchSlide()
    ) { route ->
        val viewModel = koinViewModel<SearchContentViewModel>(
            key = "SearchContent:${route.bookUrl}",
            parameters = { parametersOf(route) }
        )
        SearchContentRouteScreen(
            viewModel = viewModel,
            isTopRoute = backStack.lastOrNull() == route,
            predictiveBackEnabled = configuration.appShell.predictiveBackEnabled,
            autoFocus = route.autoFocus,
            onBack = { onNavigateBack() },
        )
    }



    // 与内置浏览器一致：不覆盖宿主转场，交由 NavDisplay 的全局 transitionSpec 结算。



    entry<MainRouteReadRecord> {
        ReadRecordRouteScreen(
            onBackClick = { onNavigateBack() },
            onBookClick = { name, author ->
                lifecycleScope.launch {
                    val book = withContext(IO) {
                        io.legado.app.data.appDb.bookDao.getBook(name, author)
                    }
                    if (book != null) this@mainEntryProvider.startActivityForBook(book)
                    else {
                        // exp.11：全局搜索已移除，本地无此书时只提示。
                        this@mainEntryProvider.toastOnUi("本地书架中没有这本书")
                    }
                }
            },
            onSummaryClick = {
                onNavigateToRoute(MainRouteReadRecordOverview)
            }
        )
    }

    entry<MainRouteReadRecordOverview> {
        ReadRecordOverviewRouteScreen(
            onBackClick = { onNavigateBack() },
            onBookClick = { name, author ->
                lifecycleScope.launch {
                    val book = withContext(IO) {
                        io.legado.app.data.appDb.bookDao.getBook(name, author)
                    }
                    if (book != null) this@mainEntryProvider.startActivityForBook(book)
                    else {
                        // exp.11：全局搜索已移除，本地无此书时只提示。
                        this@mainEntryProvider.toastOnUi("本地书架中没有这本书")
                    }
                }
            }
        )
    }

    entry<MainRouteBookInfo>(
        metadata = bookInfoEntryMetadata(configuration.appShell.predictiveBackEnabled)
    ) { route ->
        val bookInfoViewModel = koinViewModel<BookInfoViewModel>(key = "BookInfo:${route.bookUrl}")
        ConsumeBookPageResult(route, backStack.lastOrNull() == route, navRouteTracker) { result ->
            when (result) {
                is BookPageResult.ChapterSelected -> bookInfoViewModel.onTocResult(
                    Triple(result.index, result.position, false)
                )

                BookPageResult.TocCancelled -> bookInfoViewModel.onTocResult(null)
                BookPageResult.InfoEdited -> bookInfoViewModel.onInfoEdited()
                else -> Unit
            }
        }
        BookInfoRouteScreen(
            bookUrl = route.bookUrl,
            name = route.name,
            author = route.author,
            origin = route.origin,
            coverPath = route.coverPath,
            viewModel = bookInfoViewModel,
            onOpenToc = { onNavigateToRoute(MainRouteToc(it)) },
            onOpenInfoEdit = { onNavigateToRoute(MainRouteBookInfoEdit(it)) },
            onBack = {
                MainNavigator.navigateBack(
                    this@mainEntryProvider,
                    backStack,
                    navRouteTracker,
                    fromRoute = route,
                )
            },
            onFinish = { resultCode, _ ->
                val parent = backStack.getOrNull(backStack.lastIndex - 1)
                val popped = MainNavigator.navigateBack(
                    this@mainEntryProvider,
                    backStack,
                    navRouteTracker,
                    fromRoute = route,
                )
                if (popped) {
                    if (parent is MainRouteReadBook) {
                        navRouteTracker.reportBookInfoResult(
                            parent,
                            resultCode == Activity.RESULT_OK
                        )
                    } else if (parent is MainRouteReadManga && resultCode == Activity.RESULT_OK) {
                        navRouteTracker.reportBookPageResult(parent, BookPageResult.BookDeleted)
                    }
                }
                popped
            },
            // 私密内容需要本地密码：直接把人送到私密设置页，而不是只弹一句提示
            onOpenSettings = {
                onNavigateToRoute(MainRouteSettingsPrivate)
            },
            onOpenReader = { bookUrl, inBookshelf, chapterChanged ->
                onNavigateToRoute(
                    MainRouteReadBook(
                        bookUrl = bookUrl,
                        inBookshelf = inBookshelf,
                        chapterChanged = chapterChanged,
                        // 与详情页封面登记在同一个 key 上，封面才能从详情页继续飞进阅读页。
                        sharedCoverKey = route.sharedCoverKey ?: bookCoverSharedElementKey(bookUrl),
                    )
                )
            },
            onOpenMangaReader = { bookUrl, inBookshelf, chapterChanged ->
                onNavigateToRoute(
                    MainRouteReadManga(
                        bookUrl = bookUrl,
                        inBookshelf = inBookshelf,
                        chapterChanged = chapterChanged,
                        openRequestId = System.nanoTime(),
                        sharedCoverKey = route.sharedCoverKey ?: bookCoverSharedElementKey(bookUrl),
                    )
                )
            },
            onNavigateToBookInfo = { name, author, bookUrl, origin, coverPath ->
                onNavigateToRoute(MainRouteBookInfo(name, author, bookUrl, origin, coverPath))
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            sharedCoverKey = route.sharedCoverKey ?: bookCoverSharedElementKey(route.bookUrl),
        )
    }












    entry<MainRouteHighlightTagRule> {
        HighlightTagRuleRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    // 以下三个原先各自是一个薄壳 Activity（只为 setContent 而存在）。改成普通目的地后，
    // 宽窗口下能与首页并排成右栏，也回到 NavDisplay 的统一转场里。

    entry<MainRouteDictRule> {
        DictRuleRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteTxtTocRule> { route ->
        TxtRuleRouteScreen(
            initialRule = route.initialRule,
            // 选择模式：为打开它的目录页挑一条规则，选中即回传并退出。
            onPickRule = route.pickForBookUrl?.let {
                { rule ->
                    closeBookPage(backStack, route, BookPageResult.TxtTocRuleChosen(rule))
                }
            },
            onBackClick = { onNavigateBack() },
        )
    }

    entry<MainRouteTxtTocRulePreview> { route ->
        TxtTocRulePreviewRouteScreen(
            bookUrl = route.bookUrl,
            currentTocRegex = route.currentTocRegex,
            onBack = { onNavigateBack() },
            onApplyRule = { rule ->
                closeBookPage(backStack, route, BookPageResult.TxtTocRuleChosen(rule))
            },
            onOpenManagePage = { onNavigateToRoute(MainRouteTxtTocRule()) },
        )
    }

    entry<MainRouteAllBookmark> {
        AllBookmarkRouteScreen(onBack = { onNavigateBack() })
    }








    entry<MainRouteAbout> {
        val viewModel = koinViewModel<AboutViewModel>()
        val context = LocalContext.current
        LaunchedEffect(viewModel) {
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    is AboutEffect.OpenUrl -> context.openUrl(effect.url)
                    is AboutEffect.ShowToast -> context.toastOnUi(effect.message)
                    is AboutEffect.StartDownload -> Download.start(
                        context,
                        effect.url,
                        effect.fileName
                    )
                }
            }
        }
        AboutScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            onBack = { onNavigateBack() },
        )
    }
}

/** 有声书的 Android 宿主动作；播放页与听书页共用同窗口形变组件。 */