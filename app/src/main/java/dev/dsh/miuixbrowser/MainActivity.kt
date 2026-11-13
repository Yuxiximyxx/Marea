package dev.dsh.miuixbrowser

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class MainActivity : ComponentActivity() {

    /** 从其他应用点链接进来时要打开的地址 */
    private val incomingUrl = mutableStateOf<String?>(null)

    /** 点下载通知进来时要求打开下载页 */
    private val openDownloads = mutableStateOf(false)

    /** 本次外部链接是否已被规则送出处理，已处理则不进浏览器主界面 */
    private var externalHandled = false

    /** 本次外部链接已处理完，onResume 后收尾：冷启动 finish、已运行 moveTaskToBack（下载跳板转交的已运行收尾例外，见 [retreatViaTrampoline]） */
    private var retreatAfterResume = false

    /**
     * fork.17：本次外部直链命中下载形态、已由跳板转交下载弹窗。
     * 冷启时置位后 onCreate 不再 setContent（BrowserScreen 不组合、主页不加载），
     * 退场收尾复用 [externalHandled] 的 onResume 通道。每次 consumeViewIntent 开头
     * 先清零，只反映「当前正在消费的这条意图」。
     */
    private var downloadTrampoline = false

    /**
     * fork.19：本次 onResume 收尾是否源于下载跳板转交（在置 [retreatAfterResume]
     * 的同一处快照）。弹窗与主界面同任务后，已运行实例的跳板收尾不能再
     * moveTaskToBack——那会连刚拉起的弹窗一起把整个任务沉底；改为不退场，
     * 弹窗叠在本 Activity 之上、关掉正好回到转交前的浏览器状态。
     * 规则送出（LinkRouter.Dispatched）的收尾不受影响，仍走 moveTaskToBack。
     */
    private var retreatViaTrampoline = false

    /** 本次处理发生在冷启动（onCreate）路径，收尾用 finish 而不是 moveTaskToBack */
    private var coldExternal = false

    /**
     * fork.20：本次外部链接的规则判定需要先做短链预解析（异步未回）。
     * 同步收尾登记（onCreate / onNewIntent 返回后那段）会错过它，回调里要补登记；
     * 冷启时在判定回来之前先不组合浏览器界面，Unhandled 回落时再补组合。
     */
    private var externalResolvePending = false

    /** 浏览器界面是否已经 setContent（异步回落时补组合的判据，避免重复 setContent） */
    private var browserComposed = false

    /** 当前是否 resumed：异步回调晚于 onResume 时直接补收尾的判据 */
    private var activityResumed = false

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"

        /** 下载弹窗探测发现是网页后回主界面打开的标记：带标记的 VIEW 意图不再被外链下载判定重复拦截 */
        const val EXTRA_OPEN_IN_BROWSER = "open_in_browser"

        /** exp.16：隐私模式「进程级后台自动退出」观察者只注册一次（MainActivity 会重建，观察者挂在进程生命周期上不能重复挂） */
        @Volatile
        private var privacyAutoExitRegistered = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // fork.7 步进日志：外部链接进入的第一现场——action 与完整 URL 原样落盘，
        // 在任何过滤 / 分诊之前，先确认 GitHub 等外部 App 到底发来了什么形态的意图
        Diag.log("MainActivity.onNewIntent: action=${intent.action} data=${intent.data}")
        setIntent(intent)
        GmNotifications.consumeClick(intent)
        consumeOpenDownloads(intent)
        consumeViewIntent(intent)
        if (externalHandled) {
            // 收尾推迟到 onResume 统一做：规则送出后不在这里当场 finish / 退后台
            retreatViaTrampoline = downloadTrampoline
            retreatAfterResume = true
        }
    }

    private fun consumeOpenDownloads(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true) {
            openDownloads.value = true
            intent.removeExtra(EXTRA_OPEN_DOWNLOADS)
        }
    }

    private fun consumeViewIntent(intent: Intent?) {
        // 跳板标志只反映当前这条意图：先清零，命中下载形态时在分诊里重新置位，
        // 避免上一条意图的残留让 onResume 收尾误判来源
        downloadTrampoline = false
        if (intent?.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        val scheme = data.scheme?.lowercase() ?: return
        if (scheme != "http" && scheme != "https") return
        val settings = AppSettings(this)
        Diag.log("consumeViewIntent: action=${intent.action} data=$data linkOpenMode=${settings.linkOpenMode}")
        when (settings.linkOpenMode) {
            1 -> openWithCustomTab(data)
            2 -> forwardToOtherBrowser(data)
            else -> {
                val url = data.toString()
                // fork.24：外部下载形态链接先分诊、后规则。旧顺序是先过 LinkRouter：
                // 带短链预解析的规则（如他启用的「GitHub 下载」）会在分诊之前先发
                // 网络解析、再把解析后的直链送去外部应用，主界面在这段等待期以
                // 不透明主题底色（夜间 #111）全屏盖住来源应用（2026-11-12 真机录屏：
                // 黑屏约 2~3 秒后才出 ABDM 面板）。外部下载本就该由 Marea 自有面板
                // 接管：looksLikeFileUrl 是纯 URL 形态的同步判定（无网络），命中即
                // 在 onCreate 内同步转交下载弹窗，「发送到其他应用」类规则在这条
                // 路径上不再抢跑（规则数据不动；站内导航、长按菜单等其它分发路径
                // 不受影响）。弹窗逃生口回来的意图不重复拦截（防循环，与分诊内同判）。
                val fromDialogEscape = intent.getBooleanExtra(EXTRA_OPEN_IN_BROWSER, false)
                if (!fromDialogEscape && Downloader.looksLikeFileUrl(url)) {
                    Diag.log("MainActivity: file-like external url triaged before rules (fork.24): ${url.take(100)}")
                    handleExternalUnhandled(intent, url)
                    return
                }
                // 分发规则仍走主界面这条原路径（见 LinkRouter）：命中送出则不进浏览器。
                // fork.20：带短链预解析的规则改走异步判定——同步完成的链接行为与旧版
                // 完全一致；异步的在回调里补收尾登记（见 externalResolvePending）。
                if (LinkRouter.needsResolve(url)) externalResolvePending = true
                LinkRouter.routeExternalAsync(this, url) { result ->
                    when (result) {
                        LinkRouter.Result.Dispatched -> {
                            externalHandled = true
                            if (externalResolvePending) {
                                externalResolvePending = false
                                retreatViaTrampoline = downloadTrampoline
                                retreatAfterResume = true
                                if (activityResumed) performArmedRetreat()
                            }
                        }
                        LinkRouter.Result.Unhandled -> handleExternalUnhandled(intent, url)
                    }
                }
            }
        }
    }

    /**
     * 外部链接未被规则送出后的分诊（同步判定与短链异步回落共用）：
     * 命中下载形态由主界面当跳板转交下载弹窗，其余进浏览器打开。
     */
    private fun handleExternalUnhandled(intent: Intent, url: String) {
        val wasPending = externalResolvePending
        externalResolvePending = false
        // fork.17：规则没接的链接里，命中下载形态的由主界面当跳板转交下载弹窗。
        // 背景（fork.7/8 实证）：Marea 是默认浏览器时，GitHub 签名直链这类意图被系统
        // 直接判给 MainActivity，弹窗的 intent-filter 没机会上场——仍走应用内显式拉起，
        // 判定只认既有域名/扩展名清单（Downloader.looksLikeFileUrl），普通网页链接不命中。
        // fork.19：弹窗已与主界面合回同一任务（去掉 fork.17 的独立 taskAffinity 与
        // 恒 NEW_TASK）——拆任务当时是怀疑任务归属让弹窗不可见，fork.18 已证明真凶
        // 是 miuix 覆盖层组件并换成普通内联面板、真机确认可见。同任务后转交只发生
        // 一次窗口切换、无中间黑闪。退场语义按路径分：冷启收尾 finish（任务里只剩
        // 弹窗，关掉即回来源应用，ABDM 跳板同款）；已运行实例不退场，弹窗叠在现有
        // 主界面之上、关掉回原浏览器状态（见 onResume 的 retreatViaTrampoline 分支），
        // 且冷启时不再 setContent——浏览器界面全程不出现。拉起失败才落回浏览器打开。
        // 弹窗探测发现是网页时带 EXTRA_OPEN_IN_BROWSER 回来，这类意图不再重复
        // 拦截（防循环）。
        val fromDialogEscape = intent.getBooleanExtra(EXTRA_OPEN_IN_BROWSER, false)
        val fileLike = Downloader.looksLikeFileUrl(url)
        // fork.15 步进：外部分诊的输入与判定结果都落盘（规则结果见 LinkRouter 日志），
        // 真机再出问题时能直接读出链接在哪一环被放行进了浏览器
        Diag.log("MainActivity triage: host=${intent.data?.host} fileLike=$fileLike escape=$fromDialogEscape")
        if (!fromDialogEscape && fileLike) {
            Diag.log("MainActivity: file-like external url -> download dialog (trampoline): ${url.take(100)}")
            val launched = runCatching {
                startActivity(DownloadDialogActivity.intent(this, url))
            }.isSuccess
            Diag.log("MainActivity: download dialog trampoline launched=$launched")
            if (launched) {
                downloadTrampoline = true
                externalHandled = true
                if (wasPending) {
                    // fork.20：异步回落才命中跳板，同步收尾登记已错过，在这里补
                    retreatViaTrampoline = true
                    retreatAfterResume = true
                    if (activityResumed) performArmedRetreat()
                }
            } else {
                incomingUrl.value = url
            }
        } else {
            incomingUrl.value = url
        }
        // fork.20：冷启时为等异步判定推迟了组合，落到浏览器打开就在这里补上
        if (wasPending && !browserComposed && !downloadTrampoline) composeBrowser()
    }


    /** Chrome 自定义标签页 */
    private fun openWithCustomTab(uri: Uri) {
        runCatching {
            val intent = androidx.browser.customtabs.CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            intent.launchUrl(this, uri)
        }.onFailure { incomingUrl.value = uri.toString() }
    }

    /** 设置里选了「交给其他浏览器」时，弹出选择器并排除自己 */
    private fun forwardToOtherBrowser(uri: Uri) {
        runCatching {
            val view = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            val chooser = Intent.createChooser(view, "打开链接").apply {
                putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS,
                    arrayOf(ComponentName(this@MainActivity, MainActivity::class.java)),
                )
            }
            startActivity(chooser)
        }
    }

    /**
     * 规则送出 / 下载跳板的收尾统一放在 onResume：送出发生在 onCreate / onNewIntent，当场
     * finish / moveTaskToBack 的时序曾让外部表现异常，推迟到 Activity 真正到前台后再退场。
     * （fork.19：下载弹窗已同任务——冷启跳板仍 finish，已运行跳板改为不退场，
     * 见 [retreatViaTrampoline]；规则送出的冷启 finish / 已运行 moveTaskToBack 不变。）
     */
    override fun onResume() {
        super.onResume()
        activityResumed = true
        performArmedRetreat()
    }

    override fun onPause() {
        activityResumed = false
        super.onPause()
    }

    /**
     * 已登记的外部处理收尾真正执行（只执行一次）：onResume 调用；
     * fork.20 的短链异步判定回落晚于 onResume 时，回调里也调它补收尾。
     */
    private fun performArmedRetreat() {
        if (!retreatAfterResume) return
        retreatAfterResume = false
        externalHandled = false
        val viaTrampoline = retreatViaTrampoline
        retreatViaTrampoline = false
        if (coldExternal) {
            coldExternal = false
            finish()
        } else if (viaTrampoline) {
            // fork.19：已运行实例的下载跳板不退场——弹窗与主界面同任务，弹窗正叠在
            // 本 Activity 之上，moveTaskToBack 会连弹窗一起把整个任务沉底；
            // 留在这里，弹窗关闭后正好回到转交前的浏览器状态。
            Diag.log("MainActivity: download trampoline on running instance, dialog stacked in same task, stay (no retreat)")
        } else {
            moveTaskToBack(true)
        }
    }

    /**
     * exp.16：隐私模式「切后台 / 锁屏自动退出」改挂进程级生命周期判定。
     * 旧实现挂在本 Activity 的 onStop：应用内任何不透明 Activity（打开下载 txt 的
     * LegadoReaderHostActivity、Legado 书架 MainActivity 等）被拉到前台时，浏览器
     * MainActivity 同样走 onStop，隐私会话被误杀——书是按空间正确导入的，坏的是
     * 返回浏览器后标签 / 下载 / 书签全被切回普通空间。ProcessLifecycleOwner 的
     * ON_STOP 只在全应用没有 started 状态的 Activity 时才派发：应用内跳转时新页
     * 先 start、旧页后 stop，started 计数不归零、不会误触；真正切后台 / 锁屏时
     * 全部 Activity stop，照样触发，符合原口径。观察者只注册一次（见 companion 标志）。
     */
    private fun registerPrivacyAutoExitOnBackground() {
        if (privacyAutoExitRegistered) return
        privacyAutoExitRegistered = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (PrivacyMode.active) {
                    Diag.log("Privacy: process background (ProcessLifecycleOwner ON_STOP), auto exit privacy mode")
                    PrivacyMode.exit()
                }
            }
        })
    }

    /** 图标切换推迟到离开应用时再生效，避免正在前台的入口被停用导致界面被系统关掉 */
    override fun onStop() {
        super.onStop()
        // 隐私模式自动退出不在这里做（会误杀应用内跳转，见 registerPrivacyAutoExitOnBackground）
        // 图标切换推迟到离开应用时再生效，避免正在前台的入口被停用导致界面被系统关掉；
        // 「默认」图标走日夜自动（fork.5），手动选的图标走原同步逻辑
        val wanted = AppSettings(this).appIcon
        if (wanted == 0) {
            AppIconManager.applyAutoDayNight(this)
        } else if (AppIconManager.current(this) != wanted) {
            AppIconManager.apply(this, wanted)
        }
    }

    /** 系统日夜切换时 Activity 不重建（configChanges 含 uiMode），在这里同步「默认」图标 */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (AppSettings(this).appIcon == 0) AppIconManager.applyAutoDayNight(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLogger()
        dev.dsh.miuixbrowser.Diag.init(this)
        dev.dsh.miuixbrowser.Diag.log("MainActivity.onCreate")
        registerPrivacyAutoExitOnBackground()
        dev.dsh.miuixbrowser.Diag.log("MainActivity.onCreate intent: action=${intent.action} data=${intent.data}")
        AdBlock.init(this)
        CacheCleaner.runOnColdStart(this)
        // 下载任务落盘恢复（杀进程/重启后未完成任务可真续传）+ 链接分发引擎初始化。
        // fork.6：必须赶在下面 consumeViewIntent 分诊之前——fork.5 时 init 在分诊之后，
        // 冷启动 match() 因未初始化恒返回 null，规则判定与回退链在冷启动下全程静默失效。
        // 上游 2.0.0 起 restore 改名为 init（从磁盘恢复下载任务列表，语义不变）
        Downloader.init(this)
        LinkDispatch.init(this)
        // 上游 2.0.0 新增：Cookie 管控冷启动清理
        CookieControl.runOnColdStart(this, AppSettings(this))
        // fork.9：WebView 多 Profile 支持检测在进程启动时、主线程上先算好并缓存——
        // fork.5~8 里这个检测的进程内首次调用发生在隐私设置页组合阶段，其后约 1.5 秒
        // 进程必以原生 SIGSEGV 死亡（页面本身零交互即死、JVM 无异常）。把首次触碰挪到
        // 启动语境后，设置页只读缓存 Boolean、零原生接触；前后各留一行 Diag 便于复测对时。
        dev.dsh.miuixbrowser.Diag.log("Privacy: profile support warm-up begin (startup, main thread)")
        val profileWarm = dev.dsh.miuixbrowser.PrivacyMode.profileSupported()
        dev.dsh.miuixbrowser.Diag.log("Privacy: profile support warm-up done: $profileWarm")
        // 系统重建 Activity（savedInstanceState != null）时 intent 还是当初那条，不能再重复打开一次链接
        if (savedInstanceState == null) {
            consumeOpenDownloads(intent)
            consumeViewIntent(intent)
            if (externalHandled) {
                // 冷启动被分诊处理：不在 onCreate 里 finish（弹窗还没拉起就 finish 会把启动连带吞掉），
                // 只登记，等 onResume 拉起弹窗 / 确认送出后再收尾（见 onResume）
                coldExternal = true
                retreatViaTrampoline = downloadTrampoline
                retreatAfterResume = true
            } else if (externalResolvePending) {
                // fork.20：短链预解析还在路上——先按冷启登记，判定回来后在回调里补
                // 收尾（Dispatched / 跳板）或补组合浏览器界面（Unhandled 进浏览器）
                coldExternal = true
            }
        }
        // 「默认」图标按当前系统日夜状态对齐（冷启动时启动器还没把本应用摆到前台，切换最安全）
        if (AppSettings(this).appIcon == 0) AppIconManager.applyAutoDayNight(this)

        // 全屏沉浸：内容画到状态栏和手势小白条下面，小白条保留可见。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (downloadTrampoline || externalResolvePending) {
            // fork.17 跳板冷启：直链已转交同一任务的下载弹窗，主界面到此为止——不请求
            // 通知权限、不 setContent（BrowserScreen 不组合、主页与标签都不加载），
            // onResume 收尾时 finish，本 Activity 退出后任务里只剩下载弹窗压在来源应用之上。
            // fork.20：短链预解析未回时同样先不组合，判定回落 Unhandled 再补 composeBrowser()。
            Diag.log("MainActivity: download trampoline launch, skip browser UI")
        } else {
            composeBrowser()
        }
    }

    /** 组合浏览器主界面（onCreate 同步路径与短链异步回落补组合共用，只执行一次） */
    private fun composeBrowser() {
        if (browserComposed) return
        browserComposed = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        setContent {
            val context = LocalContext.current
            val settings = remember { AppSettings(context) }
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.themeMode) {
                1 -> false
                2 -> true
                else -> systemDark
            }
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            val mode = when (settings.themeMode) {
                1 -> ColorSchemeMode.Light
                2 -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            }
            val themeController = remember(mode) { ThemeController(mode) }
            MiuixTheme(controller = themeController) {
                CompositionLocalProvider(LocalBlurMode provides settings.blurMode) {
                    BrowserScreen(
                        settings = settings,
                        initialUrl = incomingUrl.value,
                        onInitialUrlConsumed = { incomingUrl.value = null },
                        openDownloads = openDownloads.value,
                        onOpenDownloadsConsumed = { openDownloads.value = false },
                    )
                }
            }
        }
    }

    /** 把未捕获异常写到应用外部目录，方便没有 adb 时排查。 */
    private fun installCrashLogger() {
        // exp.3 起统一走 CrashCapture（落盘应用私有目录 last_crash.txt，
        // 设置页「导出诊断日志」连同 Diag 一起打包）；旧版写外部目录
        // crash.txt 的记录仍会被导出捎带，不丢历史证据。
        CrashCapture.install(this)
    }
}
