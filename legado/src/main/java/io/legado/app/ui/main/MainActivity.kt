package io.legado.app.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.constant.EventBus
import io.legado.app.data.repository.ReadAloudSettingsRepository
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.help.LocalNetworkAccess
import io.legado.app.help.MareaSpace
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.LocalNetworkBlockedNotifier
import io.legado.app.help.storage.Backup
import io.legado.app.help.update.AppUpdateGitHub
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.about.MarkdownSheet
import io.legado.app.ui.about.UpdateDialog
import io.legado.app.ui.book.read.ReadBookInputHandler
import io.legado.app.ui.book.read.ReadBookRouteHost
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.main.bookshelf.BookshelfCoverPreloader
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.welcome.WelcomeActivity
import io.legado.app.ui.widget.components.navigation.shouldUseRail
import io.legado.app.ui.widget.components.privacy.PrivateAppStartGate
import io.legado.app.utils.LogUtils
import io.legado.app.utils.eventBus.FlowEventBus
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.toggleSystemBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 主界面
 */
open class MainActivity : BaseComposeActivity() {

    private data class RouteEvent(
        val route: NavKey,
        val resetToHome: Boolean,
    )

    /** 当前激活的有声书播放器 ViewModel（由有声书路由在生命周期内设置/清理） */

    /** 全局 Compose 文本弹层状态，供遗留命令式路径展示 Markdown/文本内容 */
    private val textSheetFlow = MutableStateFlow<TextSheetData?>(null)

    fun showTextSheet(title: String, content: String, onDismiss: (() -> Unit)? = null) {
        textSheetFlow.value = TextSheetData(title, content, onDismiss)
    }

    companion object {
        /** exp.10：Marea 入口传入的空间（0 普通 / 1 隐私），见 [io.legado.app.help.MareaSpace]。 */
        const val EXTRA_MAREA_SPACE = "marea_space"

        private const val KEY_RESTORE_READ_ROUTE = "restoreReadRoute"
        private const val KEY_RESTORE_READ_BOOK_URL = "restoreReadBookUrl"
        private const val KEY_RESTORE_READ_ALOUD = "restoreReadAloud"
        private const val KEY_RESTORE_READ_IN_BOOKSHELF = "restoreReadInBookshelf"
        private const val KEY_RESTORE_READ_CHAPTER_CHANGED = "restoreReadChapterChanged"

        /** 播放页收起后等待的时长：期间若重新展开（听书 ⇄ 朗读切换）就不还原本地系统栏状态。 */
        private const val SYSTEM_BARS_RESTORE_DELAY_MILLIS = 200L

        private val startupUpdateCheckGate = ProcessStartupUpdateCheckGate()

        @Volatile
        var hasActiveReadBookRoute: Boolean = false

        @Volatile
        var hasActiveSourceLoginRoute: Boolean = false

        fun createLauncherIntent(context: Context): Intent =
            MainIntent.createLauncherIntent(context)

        fun createHomeIntent(context: Context): Intent = MainIntent.createHomeIntent(context)
        fun createIntent(context: Context, configTag: String? = null): Intent =
            MainIntent.createIntent(context, configTag)

        fun createBookInfoIntent(
            context: Context,
            name: String? = null,
            author: String? = null,
            bookUrl: String,
            origin: String? = null,
            coverPath: String? = null,
        ): Intent = MainIntent.createBookInfoIntent(
            context = context,
            name = name,
            author = author,
            bookUrl = bookUrl,
            origin = origin,
            coverPath = coverPath,
        )

        fun createBookshelfManageScreenIntent(context: Context, groupId: Long = -1L): Intent =
            MainIntent.createBookshelfManageScreenIntent(context, groupId)

        fun createCacheIntent(context: Context, groupId: Long = -1L): Intent =
            MainIntent.createCacheIntent(context, groupId)

        fun createBookCacheManageIntent(context: Context): Intent =
            MainIntent.createBookCacheManageIntent(context)

        fun createReadBookIntent(
            context: Context,
            bookUrl: String? = null,
            readAloud: Boolean = false,
            inBookshelf: Boolean = true,
            chapterChanged: Boolean = false,
        ): Intent = MainIntent.createReadBookIntent(
            context = context,
            bookUrl = bookUrl,
            readAloud = readAloud,
            inBookshelf = inBookshelf,
            chapterChanged = chapterChanged,
        )

        fun createReadMangaIntent(
            context: Context,
            bookUrl: String? = null,
            inBookshelf: Boolean = true,
            chapterChanged: Boolean = false,
        ): Intent = MainIntent.createReadMangaIntent(
            context = context,
            bookUrl = bookUrl,
            inBookshelf = inBookshelf,
            chapterChanged = chapterChanged,
        )
    }

    private val viewModel by viewModel<MainViewModel>()
    private val bookshelfCoverPreloader by inject<BookshelfCoverPreloader>()
    private val otherSettingsGateway by inject<OtherSettingsGateway>()
    private val mangaSettingsGateway by inject<MangaSettingsGateway>()
    private val backupSettingsGateway by inject<BackupSettingsGateway>()
    internal val navRouteTracker by inject<MainNavRouteTracker>()
    private val routeEvents = MutableSharedFlow<RouteEvent>(extraBufferCapacity = 1)

    private val localNetworkRequestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // 授权后由用户重试原操作；未授权时提示授予方式。
        if (!granted) {
            toastOnUi(R.string.local_network_permission_required)
        }
    }
    private var shouldApplyDefaultToRead = true
    private var restoredReadBookRoute: MainRouteReadBook? = null
    internal var activeReadBookInputHandler: ReadBookInputHandler? = null
    internal var activeReadBookRoute: MainRouteReadBook? = null
    internal var activeMangaKeyHandler: ((Int) -> Boolean)? = null

    /** 听书/朗读播放浮层是否可见：可见期间由浮层接管系统栏，覆盖阅读器的隐藏策略。 */
    private var isPlaybackOverlayVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        shouldApplyDefaultToRead = savedInstanceState == null
        restoredReadBookRoute = savedInstanceState?.restoreReadBookRoute()
            ?: navRouteTracker.lastReadBookRoute()
        super.onCreate(savedInstanceState)
        // exp.10：空间先于一切路由判定落位，书架首帧即是对应空间的书。
        applyMareaSpaceFrom(intent)

        if (checkStartupRoute()) return
        val shouldAutoCheckUpdate = startupUpdateCheckGate.consume(
            otherSettingsGateway.currentSettings.autoCheckUpdateOnStart
        )

        // 局域网请求（书源 / 图源 / WebDAV）被系统拦下时，前台立即补一次本地网络权限申请。
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                FlowEventBus.with<Unit>(EventBus.LOCAL_NETWORK_PERMISSION_REQUIRED).collect {
                    requestLocalNetworkPermissionIfBlocked()
                }
            }
        }

        lifecycleScope.launch {
            //版本更新
            upVersion()
            //备份同步
            backupSync()
            //自动更新书籍
            val isAutoRefreshedBook = savedInstanceState?.getBoolean("isAutoRefreshedBook") ?: false
            if (otherSettingsGateway.currentSettings.autoRefresh && !isAutoRefreshedBook) {
                viewModel.upAllBookToc()
            }
            if (shouldAutoCheckUpdate) {
                checkUpdateOnStart()
            }
        }

        // 书架封面预热：必须早于书架首帧发起。卡片请求带了 placeholderMemoryCacheKey，
        // 内存缓存里已有同一键时 Coil 会在真实加载之前就把缓存图交给 target，于是进入书架
        // 的第一帧就是封面，而不是"灰底 → 稍后出现"。独立协程，不阻塞上面的启动关键路径；
        // 预热失败对 UI 无影响（卡片自己的请求会照常决定成功/错误态）。
        lifecycleScope.launch {
            runCatching { bookshelfCoverPreloader.preloadCurrentGroupFirstScreen() }
        }
    }

    /**
     * Android 17 (API 37) 起 Web 服务需要本地网络权限才能接受局域网入站连接。
     * 已授予直接启动；未授予先申请，授予后由 launcher 回调补启。
     */
    /**
     * exp.10：入口带了空间 extra 就应用到 [MareaSpace]（无 extra 的拉起不动当前空间，
     * 回落其持久化的上次空间）。onResume 时若本实例 intent 仍带 extra 则重申一次——
     * 多实例/回栈场景下避免另一个实例改过空间后，本实例回来展示串空间的书架。
     */
    private fun applyMareaSpaceFrom(intent: Intent?) {
        if (intent?.hasExtra(EXTRA_MAREA_SPACE) == true) {
            MareaSpace.applySpace(intent.getIntExtra(EXTRA_MAREA_SPACE, MareaSpace.NORMAL))
        }
    }

    override fun onResume() {
        super.onResume()
        applyMareaSpaceFrom(intent)
        requestLocalNetworkPermissionIfBlocked()
        if (isPlaybackOverlayVisible) {
            // 阅读器在 ON_RESUME 里会按自己的菜单状态重设系统栏（可能重新隐藏），
            // 播放浮层盖在它上面，晚一帧再把系统栏抢回来。
            window.decorView.post {
                if (isPlaybackOverlayVisible) toggleSystemBar(true)
            }
        }
    }

    /**
     * 书源 / 图源 / WebDAV 等局域网请求被系统拦掉后，网络层会留下标记（见
     * [LocalNetworkBlockedNotifier]）；回到前台时补一次权限申请，用户重试原操作即可。
     */
    private fun requestLocalNetworkPermissionIfBlocked() {
        if (!LocalNetworkBlockedNotifier.consumePermissionRequest()) return
        if (LocalNetworkAccess.isGranted(this)) return
        localNetworkRequestPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // exp.10：存活实例被另一空间的入口再次拉起时，先切空间再处理路由。
        applyMareaSpaceFrom(intent)
        if (!intent.hasExplicitStartRoute()) return
        routeEvents.tryEmit(
            RouteEvent(
                route = MainNavigator.resolveStartRoute(intent),
                resetToHome = MainIntent.shouldOpenRouteWithHomeParent(intent),
            )
        )
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    override fun Content() {
        val configuration = LocalAppUiConfiguration.current
        val predictiveBackEnabled by rememberUpdatedState(configuration.appShell.predictiveBackEnabled)
        val tabletInterface = configuration.appShell.tabletInterface
        val defaultToReadFlow = remember(otherSettingsGateway) {
            otherSettingsGateway.settings
                .map { it.defaultToRead }
                .distinctUntilChanged()
        }
        val defaultToRead by defaultToReadFlow.collectAsStateWithLifecycle(
            otherSettingsGateway.currentSettings.defaultToRead,
        )
        val mangaSettings by mangaSettingsGateway.settings.collectAsStateWithLifecycle(
            mangaSettingsGateway.currentSettings,
        )

        // 判据来自当前窗口，旋转 / 折叠展开 / 自由窗口改尺寸都会重组，见 AppAdaptiveNavigation。
        val baseAdaptiveEnvironment = currentMainAdaptiveLayoutEnvironment(
            configuration.appShell.multiPaneMode, 0.dp,
        )
        val navigationRegion = baseAdaptiveEnvironment.singlePaneBounds()
        val requestedUseRail = shouldUseRail(
            tabletInterface,
            WindowSizeClass(navigationRegion.width, navigationRegion.height),
            IntSize(
                baseAdaptiveEnvironment.windowSize.width.value.toInt(),
                baseAdaptiveEnvironment.windowSize.height.value.toInt()
            ),
        )
        var navigationRailWidth by remember(
            requestedUseRail,
            configuration.appShell.composeEngine,
            configuration.appShell.navExtended,
        ) {
            mutableStateOf(if (!requestedUseRail) 0.dp else if (configuration.appShell.navExtended) 220.dp else 96.dp)
        }
        val useRail =
            requestedUseRail && navigationRegion.width >= MainPaneMinWidth + navigationRailWidth.value
        val adaptiveEnvironment =
            baseAdaptiveEnvironment.copy(navigationRailWidth = if (useRail) navigationRailWidth else 0.dp)
        val currentAdaptiveEnvironment by rememberUpdatedState(adaptiveEnvironment)

        val startRoutes = remember(defaultToRead) {
            val resolved = MainNavigator.resolveStartRoute(intent)
            val hasExplicitStartRoute = intent?.hasExplicitStartRoute() == true
            when {
                MainIntent.shouldOpenRouteWithHomeParent(intent) -> {
                    if (resolved == MainRouteHome) {
                        arrayOf(MainRouteHome)
                    } else {
                        arrayOf(MainRouteHome, resolved)
                    }
                }
                !hasExplicitStartRoute && restoredReadBookRoute != null -> {
                    arrayOf(MainRouteHome, restoredReadBookRoute!!)
                }
                shouldApplyDefaultToRead &&
                        defaultToRead &&
                        resolved == MainRouteHome -> {
                    arrayOf(MainRouteHome, MainRouteReadBook())
                }
                else -> {
                    arrayOf(resolved)
                }
            }
        }
        val backStack = rememberNavBackStack(*startRoutes)
        val paneStack = backStack.dropLastWhile {
            it is MainRouteSearchContent
        }
        val paneParentIsHome = paneStack.getOrNull(paneStack.lastIndex - 1) == MainRouteHome ||
                paneStack.size == 1 && paneStack.firstOrNull() == MainRouteHome
        val listDetailEnabled = adaptiveEnvironment.paneLayout(paneParentIsHome) != null
        val hasAdaptivePair = listDetailEnabled && paneStack.size >= 2 &&
                paneStack.takeLast(2).all { supportsMainPanes(it::class) }
        val currentHasAdaptivePair by rememberUpdatedState(hasAdaptivePair)
        val activeRoute = backStack.lastOrNull()
        val fullScreenContent =
            activeRoute is MainRouteReadBook || activeRoute is MainRouteReadManga
        val singleRegion = if (!hasAdaptivePair && !fullScreenContent &&
            adaptiveEnvironment.safeRegions().size > 1
        ) adaptiveEnvironment.singlePaneBounds() else null
        val adaptiveDisplayModifier = singleRegion?.let { bounds ->
            Modifier
                .absoluteOffset(bounds.left.dp, bounds.top.dp)
                .requiredSize(bounds.width.dp, bounds.height.dp)
                .clipToBounds()
                .consumeWindowInsets(mainAdaptiveInsets)
        } ?: Modifier.fillMaxSize()
        SideEffect { navRouteTracker.onBackStackChanged(backStack) }
        // Nav3 的默认 contentKey 还带路由类型，所以按同一规则重建一个 NavEntry 来比对栈顶。
        // 覆盖层场景用它决定「是不是最上面那层」，双栏场景用它决定左栏是展开还是收拢。
        val isTopEntry: (Any) -> Boolean = { contentKey ->
            backStack.lastOrNull()?.let { top -> NavEntry(top) {}.contentKey == contentKey } == true
        }

        SideEffect {
            shouldApplyDefaultToRead = false
        }

        LaunchedEffect(backStack) {
            routeEvents.collect { event ->
                MainNavigator.navigateToRoute(
                    backStack = backStack,
                    route = event.route,
                    tracker = navRouteTracker,
                    resetToHome = event.resetToHome,
                )
            }
        }

        LaunchedEffect(backStack) {
            snapshotFlow { backStack.toList() }
                .collect {
                    // 兜底同步：预测性返回、系统返回手势等不经过 navigateToRoute/navigateBack 的路径
                    navRouteTracker.onBackStackChanged(it)
                    MainNavigator.onBackStackChanged()
                }
        }
        SharedTransitionLayout {
            // 启动验证做成单独的 screen：门槛未过时完全不组合应用界面，
            // 因此验证页背后看不到书架/阅读界面，也没有可交互的入口
            PrivateAppStartGate {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxSize()
                    ) {
                        NavDisplay(
                            modifier = adaptiveDisplayModifier,
                            backStack = backStack,
                            // 交给 nav3 装上 SharedEntryInSceneNavEntryDecorator：这是跨 scene 保持
                            // 共享元素登记的官方机制。少了它，书架 scene 一被销毁封面的登记就没了——
                            // 打开书后「马上返回」还有封面转场、'待一会儿再返回'会退化成
                            // 普通淡入淡出。
                            sharedTransitionScope = this@SharedTransitionLayout,
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                            sceneStrategies = listOf(
                                remember {
                                    ModalOverlaySceneStrategy(
                                        isTopEntry = isTopEntry,
                                        predictiveBackEnabled = { predictiveBackEnabled },
                                    )
                                },
                                // 必须在 SinglePane 之前：宽窗口下把「列表/首页 → 详情」
                                // 合成一个双栏场景，窄窗口自己 return null 退回单栏。
                                remember(listDetailEnabled) {
                                    ListDetailSceneStrategy(
                                        isEnabled = { listDetailEnabled },
                                        isEntryRetained = { contentKey ->
                                            backStack.any { NavEntry(it) {}.contentKey == contentKey }
                                        },
                                        stackSize = { backStack.size },
                                        environment = { currentAdaptiveEnvironment },
                                        canCollapse = { !currentHasAdaptivePair },
                                    )
                                },
                                SinglePaneSceneStrategy(),
                            ),
                            transitionSpec = {
                                if (targetState is ListDetailScene) {
                                    // 双栏自己演形变：左栏从占满窗口收到窗格宽度、右栏从右侧滑进来。
                                    // 场景级再滑一次会让左栏里的首页跟着从头滑入，看起来像换了页面。
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    (slideIntoContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                        animationSpec = tween(
                                            durationMillis = NAV_SLIDE_DURATION_MILLIS,
                                            easing = FastOutSlowInEasing
                                        ),
                                        initialOffset = { fullWidth -> fullWidth }
                                    ) + fadeIn(
                                        animationSpec = tween(
                                            durationMillis = NAV_FADE_DURATION_MILLIS,
                                            easing = LinearOutSlowInEasing
                                        )
                                    )) togetherWith (slideOutOfContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                        animationSpec = tween(
                                            durationMillis = NAV_SLIDE_DURATION_MILLIS,
                                            easing = FastOutSlowInEasing
                                        ),
                                        targetOffset = { fullWidth -> fullWidth / 4 }
                                    ) + fadeOut(
                                        animationSpec = tween(
                                            durationMillis = NAV_FADE_DURATION_MILLIS,
                                            easing = LinearOutSlowInEasing
                                        )
                                    ))
                                }
                            },
                            popTransitionSpec = {
                                if (initialState is ListDetailScene) {
                                    // 返回时双栏要做「右栏滑出、左栏回满」的收拢动画，所以离场场景
                                    // 必须活到动画结束；淡出叠加在收拢之上，下方已经恢复成单栏的同一页面。
                                    EnterTransition.None togetherWith
                                            fadeOut(
                                                animationSpec = tween(
                                                    durationMillis = NAV_SLIDE_DURATION_MILLIS,
                                                    easing = LinearOutSlowInEasing
                                                )
                                            )
                                } else {
                                    (slideIntoContainer(
                                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                        animationSpec = tween(
                                            durationMillis = NAV_SLIDE_DURATION_MILLIS,
                                            easing = FastOutSlowInEasing
                                        ),
                                        initialOffset = { fullWidth -> -fullWidth / 4 }
                                    ) + fadeIn(
                                        animationSpec = tween(
                                            durationMillis = NAV_FADE_DURATION_MILLIS,
                                            easing = LinearOutSlowInEasing
                                        )
                                    )) togetherWith (scaleOut(
                                        targetScale = 0.8f,
                                        animationSpec = tween(
                                            durationMillis = NAV_SLIDE_DURATION_MILLIS,
                                            easing = FastOutSlowInEasing
                                        )
                                    ) + fadeOut(animationSpec = tween(durationMillis = NAV_FADE_DURATION_MILLIS)))
                                }
                            },
                            predictivePopTransitionSpec = { _ ->
                                (slideIntoContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                    animationSpec = tween(easing = FastOutSlowInEasing),
                                    initialOffset = { fullWidth -> -fullWidth / 4 }
                                ) + fadeIn(animationSpec = tween(easing = LinearOutSlowInEasing))) togetherWith (scaleOut(
                                    targetScale = 0.8f,
                                    animationSpec = tween(easing = FastOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween()))
                            },
                            onBack = { MainNavigator.navigateBack(this@MainActivity, backStack) },
                            entryProvider = mainEntryProvider(
                                backStack = backStack,
                                configuration = configuration,
                                showMangaUi = mangaSettings.showMangaUi,
                                useRail = useRail,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                panesEnabled = listDetailEnabled,
                                onNavigationRailWidthChanged = { navigationRailWidth = it },
                                navigateToRoute = { route ->
                                    MainNavigator.navigateToRoute(
                                        backStack,
                                        route,
                                        navRouteTracker
                                    )
                                },
                                onNavigateBack = {
                                    MainNavigator.navigateBack(
                                        this@MainActivity,
                                        backStack,
                                        navRouteTracker
                                    )
                                },
                            )
                        )
                    }
                }
                // 关闭预见性返回时由宿主在导航层之前接管返回：手势不跟手预览，
                // 松手后走普通出栈，popTransitionSpec 的返回动画照常播放。
                BackHandler(
                    enabled = !configuration.appShell.predictiveBackEnabled
                ) {
                    MainNavigator.navigateBack(this@MainActivity, backStack)
                }
            }
        }
        TextSheetHost()
    }

    @Composable
    private fun TextSheetHost() {
        val sheet by textSheetFlow.collectAsStateWithLifecycle()
        sheet?.let { data ->
            MarkdownSheet(
                show = true,
                title = data.title,
                content = data.content,
                onDismissRequest = {
                    textSheetFlow.value = null
                    data.onDismiss?.invoke()
                },
            )
        }
    }

    private fun checkStartupRoute(): Boolean {
        return when {
            LocalConfig.isFirstOpenApp -> {
                startActivity<WelcomeActivity>()
                finish()
                true
            }
            else -> false
        }
    }

    private fun checkUpdateOnStart() {
        AppUpdateGitHub.check(lifecycleScope)
            .onSuccess { updateInfo ->
                showDialogFragment(UpdateDialog(updateInfo))
            }
    }

    /**
     * 版本更新日志
     */
    private suspend fun upVersion() = suspendCoroutine<Unit?> { block ->
        if (LocalConfig.versionCode == appInfo.versionCode) {
            block.resume(null)
            return@suspendCoroutine
        }
        LocalConfig.versionCode = appInfo.versionCode
        if (!BuildConfig.DEBUG) {
            lifecycleScope.launch {
                try {
                    val info = AppUpdateGitHub.getReleaseByTag(BuildConfig.VERSION_NAME)
                    if (info != null) {
                        val dialog = UpdateDialog(info, UpdateDialog.Mode.VIEW_LOG)
                        dialog.setOnDismissListener { block.resume(null) }
                        showDialogFragment(dialog)
                    } else {
                        block.resume(null)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    block.resume(null)
                }
            }
        } else {
            block.resume(null)
        }
    }

    /**
     * 备份同步
     */
    private fun backupSync() {
        if (!backupSettingsGateway.currentSettings.autoCheckNewBackup) {
            return
        }
        lifecycleScope.launch {
            val lastBackupFile = try {
                withContext(IO) { viewModel.getLatestWebDavBackup() }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@launch
            } ?: return@launch
            if (lastBackupFile.lastModify - LocalConfig.lastBackup > DateUtils.MINUTE_IN_MILLIS) {
                LocalConfig.lastBackup = lastBackupFile.lastModify
                alert(R.string.restore, R.string.webdav_after_local_restore_confirm) {
                    cancelButton()
                    okButton {
                        viewModel.restoreWebDav(lastBackupFile.name)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (otherSettingsGateway.currentSettings.autoRefresh) {
            outState.putBoolean("isAutoRefreshedBook", true)
        }
        val readRoute = navRouteTracker.backStack.value.lastOrNull() as? MainRouteReadBook
            ?: activeReadBookRoute
        if (readRoute != null) {
            outState.putBoolean(KEY_RESTORE_READ_ROUTE, true)
            outState.putString(KEY_RESTORE_READ_BOOK_URL, readRoute.bookUrl)
            outState.putBoolean(KEY_RESTORE_READ_ALOUD, readRoute.readAloud)
            outState.putBoolean(KEY_RESTORE_READ_IN_BOOKSHELF, readRoute.inBookshelf)
            outState.putBoolean(KEY_RESTORE_READ_CHAPTER_CHANGED, readRoute.chapterChanged)
        }
    }

    private fun Bundle.restoreReadBookRoute(): MainRouteReadBook? {
        if (!getBoolean(KEY_RESTORE_READ_ROUTE, false)) return null
        return MainRouteReadBook(
            bookUrl = getString(KEY_RESTORE_READ_BOOK_URL),
            readAloud = getBoolean(KEY_RESTORE_READ_ALOUD, false),
            inBookshelf = getBoolean(KEY_RESTORE_READ_IN_BOOKSHELF, true),
            chapterChanged = getBoolean(KEY_RESTORE_READ_CHAPTER_CHANGED, false),
        )
    }

    private fun Intent.hasExplicitStartRoute(): Boolean {
        return hasExtra(MainIntent.EXTRA_START_ROUTE)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isDown = event.action == KeyEvent.ACTION_DOWN
        if (keyCode == KeyEvent.KEYCODE_MENU && isDown) {
            activeReadBookInputHandler?.toggleMenu()
            if (activeReadBookInputHandler != null) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val controller = activeReadBookInputHandler ?: return super.onGenericMotionEvent(event)
        if (0 != (event.source and InputDevice.SOURCE_CLASS_POINTER) &&
            event.action == MotionEvent.ACTION_SCROLL
        ) {
            val axisValue = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            LogUtils.d("onGenericMotionEvent", "axisValue = $axisValue")
            controller.mouseWheelPage(
                if (axisValue < 0.0f) PageDirection.NEXT else PageDirection.PREV
            )
            return true
        }
        if (0 != (event.source and InputDevice.SOURCE_CLASS_JOYSTICK) &&
            event.action == MotionEvent.ACTION_MOVE
        ) {
            val yAxis = event.getAxisValue(MotionEvent.AXIS_Y)
            if (kotlin.math.abs(yAxis) > 0.5f) {
                controller.handleKeyPage(
                    if (yAxis > 0) PageDirection.NEXT else PageDirection.PREV
                )
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (activeMangaKeyHandler?.invoke(keyCode) == true) return true
        if (activeReadBookInputHandler?.onKeyDown(keyCode, event) == true) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (activeReadBookInputHandler?.onKeyUp(keyCode, event) == true) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun setupSystemBar() {
        if (isPlaybackOverlayVisible) {
            // 配置变化重建窗口后阅读器会重新按自己的策略藏栏，播放浮层要保持在最上面。
            super.setupSystemBar()
            toggleSystemBar(true)
            return
        }
        val host = activeReadBookInputHandler as? ReadBookRouteHost
        if (host != null) {
            host.upSystemUiVisibility()
        } else {
            super.setupSystemBar()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Coroutine.async {
            BookHelp.clearInvalidCache()
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }

}

data class TextSheetData(
    val title: String,
    val content: String,
    val onDismiss: (() -> Unit)? = null,
)

class LauncherW : MainActivity()
class Launcher1 : MainActivity()
class Launcher2 : MainActivity()
class Launcher3 : MainActivity()
class Launcher4 : MainActivity()
class Launcher5 : MainActivity()
class Launcher6 : MainActivity()
class Launcher0 : MainActivity()
