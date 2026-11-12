package dev.dsh.miuixbrowser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.legado.app.domain.gateway.AppUiConfigurationGateway
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import dev.dsh.glasskit.ProvideMareaBlurMode
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.legado.app.ui.book.info.BookInfoRouteScreen
import io.legado.app.ui.book.info.BookInfoViewModel
import io.legado.app.ui.book.info.edit.BookInfoEditScreen
import io.legado.app.ui.book.info.edit.BookInfoEditViewModel
import io.legado.app.ui.book.read.ReadBookController
import io.legado.app.ui.book.read.ReadBookInitRequest
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookRouteScreen
import io.legado.app.ui.book.read.ReadBookViewModel
import io.legado.app.ui.book.read.ReaderSessionViewModel
import io.legado.app.ui.book.searchContent.SearchContentRouteScreen
import io.legado.app.ui.book.searchContent.SearchContentViewModel
import io.legado.app.ui.book.toc.TocIntent
import io.legado.app.ui.book.toc.TocRouteScreen
import io.legado.app.ui.book.toc.TocViewModel
import io.legado.app.ui.book.toc.rule.TxtRuleRouteScreen
import io.legado.app.ui.book.toc.rule.preview.TxtTocRulePreviewRouteScreen
import io.legado.app.ui.main.BookPageResult
import io.legado.app.ui.main.MainNavRouteTracker
import io.legado.app.ui.main.MainNavigator
import io.legado.app.ui.main.MainRouteBookInfo
import io.legado.app.ui.main.MainRouteBookInfoEdit
import io.legado.app.ui.main.MainRouteReadBook
import io.legado.app.ui.main.MainRouteReplaceEdit
import io.legado.app.ui.main.MainRouteReplaceRules
import io.legado.app.ui.main.MainRouteSearchContent
import io.legado.app.ui.main.MainRouteToc
import io.legado.app.ui.main.MainRouteTxtTocRule
import io.legado.app.ui.main.MainRouteTxtTocRulePreview
import io.legado.app.ui.replace.ReplaceRuleRouteScreen
import io.legado.app.ui.replace.edit.ReplaceEditRouteScreen
import io.legado.app.ui.replace.edit.ReplaceEditViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 实验分支 exp/legado-reader 的宿主：Marea 的 TXT 入口改接到整体移植进来的
 * Legado 阅读器。装配方式复刻 Legado 自己的 MainNavGraph 阅读页 entry
 * （ViewModel / Controller / RouteScreen 的接线原样），导航栈只保留阅读
 * 相关子页（目录、书内搜索、书籍信息、替换规则、本地目录规则）。
 *
 * 与 Legado 原版宿主的差异（宿主层裁剪，不改阅读行为）：
 * - 内容一律包在 AppTheme 里（同 BaseComposeActivity 经 AppUiConfigurationGateway
 *   驱动）——exp.1 漏了这层，阅读页读 LegadoTheme.colorScheme 时撞上
 *   LocalLegadoColorScheme 的 error 默认值，真机表现为黑屏 + 闪退，exp.2 补齐；
 * - 不套 PrivateReadGate 私密闸门（Marea 入口侧已有自己的空间划分）；
 * - AI 对话 / 配音选角 / TTS 引擎与缓存 / 书源编辑等在线与朗读子页不在
 *   本实验范围，菜单回调为空实现；
 * - 不做封面共享元素转场。
 */
class LegadoReaderHostActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URI = "txt_reader_uri"
        const val EXTRA_NAME = "txt_reader_name"
        const val EXTRA_SPACE = "txt_reader_space"

        fun intent(context: Context, uri: Uri, name: String?, space: Int): Intent =
            Intent(context, LegadoReaderHostActivity::class.java).apply {
                putExtra(EXTRA_URI, uri.toString())
                if (!name.isNullOrBlank()) putExtra(EXTRA_NAME, name)
                putExtra(EXTRA_SPACE, space)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    }

    private val navRouteTracker: MainNavRouteTracker by inject()
    private val appUiConfigurationGateway: AppUiConfigurationGateway by inject()

    @Volatile
    private var readerController: ReadBookController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Diag.init(this)
        // 阅读路径是当前黑屏闪退的取证对象：进阅读前先把崩溃落盘装上
        // （冷进程被外部 VIEW 直拉进本页、MainActivity 没跑过的情形也要有）
        CrashCapture.install(this)
        // 与 Legado 自家宿主 BaseComposeActivity 对齐：阅读页按 edge-to-edge 布局
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val uri: Uri? = intent?.getStringExtra(EXTRA_URI)?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: intent?.data
        // exp.10：带空间 extra 的入口（书架/下载列表/打开文件四处均经 intent() 传入）
        // 先把空间应用到 Legado 侧，导入落架与后续阅读都按该空间；外部 VIEW 直达
        // 无此 extra，不动当前空间（回落 MareaSpace 持久化的上次空间）。
        if (intent?.hasExtra(EXTRA_SPACE) == true) {
            val space = intent.getIntExtra(EXTRA_SPACE, io.legado.app.help.MareaSpace.NORMAL)
            io.legado.app.help.MareaSpace.applySpace(space)
            Diag.log("legado-host: apply space=$space")
        }
        Diag.log("legado-host: create uri=$uri")
        if (uri == null) {
            Diag.log("legado-host: no uri, finish")
            finish()
            return
        }
        lifecycleScope.launch {
            // 走 Legado 自己的本地书导入：建 Book 记录、解析书名作者、入其书架库
            val book = withContext(Dispatchers.IO) {
                runCatching { LocalBook.importFile(uri) }
                    .onFailure {
                        Diag.log("legado-host: import failed\n${it.stackTraceToString()}")
                    }
                    .getOrNull()
            }
            if (book == null) {
                // exp.1 这里是静默 finish，用户侧无任何反馈；改为可见报错 + Diag 留痕
                Toast.makeText(
                    this@LegadoReaderHostActivity,
                    "打开失败：导入书籍出错",
                    Toast.LENGTH_LONG,
                ).show()
                finish()
                return@launch
            }
            Diag.log("legado-host: import ok bookUrl=${book.bookUrl}")
            setContent {
                // exp.1 黑屏根因：宿主裸 setContent，没套 Legado 自家宿主
                // BaseComposeActivity 的 AppTheme——LocalLegadoColorScheme 的
                // 默认工厂是 error("No ColorScheme provided")，阅读页组合期多处
                // 读 LegadoTheme.colorScheme，一读即抛、页面永不渲染并拖垮进程。
                // 这里按 BaseComposeActivity 原样补齐主题环境后再挂导航。
                val uiConfiguration by appUiConfigurationGateway.configuration
                    .collectAsStateWithLifecycle(appUiConfigurationGateway.currentConfiguration)
                AppTheme(configuration = uiConfiguration) {
                    // exp.7 沉浸：本宿主没有 BaseComposeActivity 的窗口背景同步，
                    // 未绘制区露的是主题动态色、与 Legado 页面底色异色成带；
                    // 补一层与 Legado 宿主同口径的全幅页面底色（阅读页正文背景
                    // 自绘于其上，不受影响），并注入 Marea 材质档位。
                    val pageBackground =
                        if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) {
                            // exp.9：与 BaseComposeActivity 同口径改绑 background
                            //（Miuix 暗色 surface 为纯黑，不是页面底色）。
                            MiuixTheme.colorScheme.background
                        } else {
                            LegadoTheme.colorScheme.background
                        }
                    ProvideMareaBlurMode {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(pageBackground)
                        ) {
                            LegadoReaderNav(bookUrl = book.bookUrl)
                        }
                    }
                }
            }
            Diag.log("legado-host: content set")
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        // 同 BaseComposeActivity：系统日夜翻转时把网关的系统暗色标记同步过来，
        // 否则宿主长驻时阅读页拿到的仍是旧的日夜判断
        appUiConfigurationGateway.synchronizeSystemDarkTheme(
            (newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        )
        super.onConfigurationChanged(newConfig)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (readerController?.onKeyDown(keyCode, event) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (readerController?.onKeyUp(keyCode, event) == true) return true
        return super.onKeyUp(keyCode, event)
    }

    /** 等价于 Legado MainNavigator.navigateBack 的单栈版本：栈空即关页 */
    private fun navigateBack(backStack: MutableList<NavKey>, fromRoute: NavKey? = null): Boolean {
        if (fromRoute != null && backStack.lastOrNull() != fromRoute) return false
        return if (backStack.size > 1) {
            backStack.removeLastOrNull()
            navRouteTracker.onBackStackChanged(backStack)
            true
        } else {
            finish()
            true
        }
    }

    /** 等价于 Legado MainActivity.closeBookPage：关子页并把结果回传父页 */
    private fun closeBookPage(
        backStack: MutableList<NavKey>,
        fromRoute: NavKey? = backStack.lastOrNull(),
        result: BookPageResult? = null,
    ): Boolean {
        if (fromRoute == null || backStack.lastOrNull() != fromRoute) return false
        currentFocus?.let { focused ->
            focused.clearFocus()
        }
        val resolvedResult = result ?: when (fromRoute) {
            is MainRouteToc -> BookPageResult.TocCancelled
            is MainRouteReplaceRules, is MainRouteReplaceEdit -> BookPageResult.RulesClosed
            else -> null
        }
        val parent = backStack.getOrNull(backStack.lastIndex - 1)
        val popped = navigateBack(backStack, fromRoute)
        if (popped && resolvedResult != null &&
            (parent is MainRouteReadBook || parent is MainRouteBookInfo || parent is MainRouteToc)
        ) {
            navRouteTracker.reportBookPageResult(parent, resolvedResult)
        }
        return popped
    }

    @Composable
    private fun ConsumeResult(
        route: NavKey,
        isTop: Boolean,
        onResult: suspend (BookPageResult) -> Unit,
    ) {
        val results by navRouteTracker.bookPageResults.collectAsStateWithLifecycle()
        val lifecycleOwner = LocalLifecycleOwner.current
        val result = results[route]
        LaunchedEffect(route, isTop, result, lifecycleOwner) {
            if (isTop && result != null) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    if (navRouteTracker.currentRoute == route &&
                        navRouteTracker.bookPageResults.value[route] == result
                    ) {
                        onResult(result)
                        navRouteTracker.takeBookPageResult(route)
                    }
                }
            }
        }
    }

    @Composable
    private fun LegadoReaderNav(bookUrl: String) {
        val backStack = rememberNavBackStack(MainRouteReadBook(bookUrl = bookUrl, inBookshelf = true))
        LaunchedEffect(backStack) {
            snapshotFlow { backStack.lastOrNull() }.collect {
                navRouteTracker.onBackStackChanged(backStack)
            }
        }
        fun navigateToRoute(route: NavKey) {
            backStack.add(route)
            navRouteTracker.onBackStackChanged(backStack)
        }
        NavDisplay(
            backStack = backStack,
            onBack = { navigateBack(backStack) },
            entryProvider = entryProvider<NavKey> {
                entry<MainRouteReadBook> { route ->
                    val readBookViewModel = koinViewModel<ReadBookViewModel>(
                        key = "ReadBook:${route.bookUrl ?: "last-read"}"
                    )
                    val readerSessionViewModel = koinViewModel<ReaderSessionViewModel>(
                        key = "ReaderSession:${route.bookUrl ?: "last-read"}"
                    )
                    val controller = remember(readBookViewModel, readerSessionViewModel) {
                        ReadBookController(
                            this@LegadoReaderHostActivity,
                            readBookViewModel,
                            readerSessionViewModel,
                            routeBookUrl = route.bookUrl,
                        )
                    }
                    val lifecycleOwner = LocalLifecycleOwner.current
                    val initRequest = remember(route) {
                        ReadBookInitRequest(
                            bookUrl = route.bookUrl,
                            inBookshelf = route.inBookshelf,
                            chapterChanged = route.chapterChanged,
                        )
                    }
                    val effectsReady = remember(readBookViewModel) { CompletableDeferred<Unit>() }
                    ConsumeResult(
                        route,
                        backStack.lastOrNull() == route,
                    ) { result ->
                        effectsReady.await()
                        when (result) {
                            is BookPageResult.ChapterSelected -> readBookViewModel.onIntent(
                                ReadBookIntent.OpenChapterResult(result.index, result.position)
                            )

                            is BookPageResult.TxtTocRuleChosen -> readBookViewModel.onIntent(
                                ReadBookIntent.TocRegexResult(result.rule)
                            )

                            BookPageResult.RulesClosed -> readBookViewModel.onIntent(
                                ReadBookIntent.ReplaceRuleResult
                            )

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
                        isTopRoute = (backStack.lastOrNull() as? MainRouteReadBook)?.let {
                            route.bookUrl == null || it.bookUrl == route.bookUrl
                        } ?: false,
                        onEffectsReady = { effectsReady.complete(Unit) },
                        onOpenToc = { tocBookUrl, page ->
                            navigateToRoute(MainRouteToc(tocBookUrl, page))
                        },
                        onOpenTocRulePreview = { tocBookUrl, tocRegex ->
                            navigateToRoute(
                                MainRouteTxtTocRulePreview(bookUrl = tocBookUrl, currentTocRegex = tocRegex)
                            )
                        },
                        onOpenReplaceRule = { ruleBookUrl, editor ->
                            navigateToRoute(
                                editor?.let { MainRouteReplaceEdit(it) }
                                    ?: MainRouteReplaceRules(ruleBookUrl)
                            )
                        },
                        onOpenBookInfo = { name, author, infoBookUrl ->
                            navigateToRoute(
                                MainRouteBookInfo(
                                    name, author, infoBookUrl,
                                    openRequestId = System.nanoTime(),
                                )
                            )
                        },
                        onOpenSearch = { word, searchBookUrl, autoFocus ->
                            navigateToRoute(
                                MainRouteSearchContent(
                                    bookUrl = searchBookUrl,
                                    searchWord = word,
                                    searchResultIndex = readBookViewModel.uiState.value.searchResultIndex,
                                    autoFocus = autoFocus,
                                )
                            )
                        },
                        // 以下子页不在本实验范围（AI / 朗读配音 / TTS / 书源），回调留空
                        onNavigateBack = {
                            navigateBack(backStack, route)
                        },
                    )
                    DisposableEffect(controller, lifecycleOwner, route.readAloud) {
                        readerController = controller
                        controller.onClose = { navigateBack(backStack) }
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
                            if (readerController === controller) {
                                readerController = null
                            }
                            controller.onClose = null
                            controller.clearTts()
                        }
                    }
                    LaunchedEffect(route, readBookViewModel, lifecycleOwner) {
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
                entry<MainRouteToc> { route ->
                    val tocViewModel = koinViewModel<TocViewModel>()
                    ConsumeResult(route, backStack.lastOrNull() == route) { result ->
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
                            navigateToRoute(
                                editor?.let { MainRouteReplaceEdit(it) }
                                    ?: MainRouteReplaceRules(route.bookUrl)
                            )
                        },
                        onPickLocalTocRule = { regex ->
                            navigateToRoute(
                                MainRouteTxtTocRule(
                                    initialRule = regex,
                                    pickForBookUrl = route.bookUrl,
                                )
                            )
                        },
                    )
                }
                entry<MainRouteTxtTocRule> { route ->
                    TxtRuleRouteScreen(
                        initialRule = route.initialRule,
                        onPickRule = route.pickForBookUrl?.let {
                            { regex: String ->
                                closeBookPage(backStack, route, BookPageResult.TxtTocRuleChosen(regex))
                            }
                        },
                        onBackClick = { navigateBack(backStack) },
                    )
                }
                entry<MainRouteTxtTocRulePreview> { route ->
                    TxtTocRulePreviewRouteScreen(
                        bookUrl = route.bookUrl,
                        currentTocRegex = route.currentTocRegex,
                        onBack = { navigateBack(backStack) },
                        onApplyRule = { rule ->
                            closeBookPage(backStack, route, BookPageResult.TxtTocRuleChosen(rule))
                        },
                        onOpenManagePage = { navigateToRoute(MainRouteTxtTocRule()) },
                    )
                }
                entry<MainRouteReplaceRules> { route ->
                    ReplaceRuleRouteScreen(
                        bookUrl = route.bookUrl,
                        onBackClick = { closeBookPage(backStack, route) },
                        onNavigateToEdit = { navigateToRoute(MainRouteReplaceEdit(it)) },
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
                entry<MainRouteSearchContent> { route ->
                    val viewModel = koinViewModel<SearchContentViewModel>(
                        key = "SearchContent:${route.bookUrl}",
                        parameters = { parametersOf(route) }
                    )
                    SearchContentRouteScreen(
                        viewModel = viewModel,
                        isTopRoute = backStack.lastOrNull() == route,
                        predictiveBackEnabled = false,
                        autoFocus = route.autoFocus,
                        onBack = { navigateBack(backStack) },
                    )
                }
                entry<MainRouteBookInfo> { route ->
                    val bookInfoViewModel = koinViewModel<BookInfoViewModel>(
                        key = "BookInfo:${route.bookUrl}"
                    )
                    ConsumeResult(route, backStack.lastOrNull() == route) { result ->
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
                        onOpenToc = { navigateToRoute(MainRouteToc(it)) },
                        onOpenInfoEdit = { navigateToRoute(MainRouteBookInfoEdit(it)) },
                        onBack = { navigateBack(backStack, route) },
                        onFinish = { resultCode, _ ->
                            val parent = backStack.getOrNull(backStack.lastIndex - 1)
                            val popped = navigateBack(backStack, route)
                            if (popped && parent is MainRouteReadBook) {
                                navRouteTracker.reportBookInfoResult(
                                    parent,
                                    resultCode == Activity.RESULT_OK
                                )
                            }
                            popped
                        },
                        // 以下目标页不在本实验宿主范围
                        onOpenSearch = { },
                        onOpenSettings = { },
                        onOpenReader = { readerBookUrl, inBookshelf, chapterChanged ->
                            navigateToRoute(
                                MainRouteReadBook(
                                    bookUrl = readerBookUrl,
                                    inBookshelf = inBookshelf,
                                    chapterChanged = chapterChanged,
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
            },
        )
    }
}
