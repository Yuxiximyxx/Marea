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

    /** 本次外部链接已处理完，onResume 后收尾：冷启动 finish、已运行 moveTaskToBack */
    private var retreatAfterResume = false

    /**
     * fork.17：本次外部直链命中下载形态、已由跳板转交独立任务的下载弹窗。
     * 冷启时置位后 onCreate 不再 setContent（BrowserScreen 不组合、主页不加载），
     * 退场收尾复用 [externalHandled] 的 onResume 通道。
     */
    private var downloadTrampoline = false

    /** 本次处理发生在冷启动（onCreate）路径，收尾用 finish 而不是 moveTaskToBack */
    private var coldExternal = false

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"

        /** 下载弹窗探测发现是网页后回主界面打开的标记：带标记的 VIEW 意图不再被外链下载判定重复拦截 */
        const val EXTRA_OPEN_IN_BROWSER = "open_in_browser"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // fork.7 步进日志：外部链接进入的第一现场——action 与完整 URL 原样落盘，
        // 在任何过滤 / 分诊之前，先确认 GitHub 等外部 App 到底发来了什么形态的意图
        Diag.log("MainActivity.onNewIntent: action=${intent.action} data=${intent.data}")
        setIntent(intent)
        consumeOpenDownloads(intent)
        consumeViewIntent(intent)
        if (externalHandled) {
            // 收尾推迟到 onResume 统一做：规则送出后不在这里当场 finish / 退后台
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
                // 分发规则仍走主界面这条原路径（见 LinkRouter）：命中送出则不进浏览器。
                val url = data.toString()
                when (LinkRouter.routeExternal(this, url)) {
                    LinkRouter.Result.Dispatched -> externalHandled = true
                    LinkRouter.Result.Unhandled -> {
                        // fork.17：规则没接的链接里，命中下载形态的由主界面当跳板转交下载弹窗。
                        // 背景（fork.7/8 实证）：Marea 是默认浏览器时，GitHub 签名直链这类意图被系统
                        // 直接判给 MainActivity，弹窗的 intent-filter 没机会上场——仍走应用内显式拉起，
                        // 判定只认既有域名/扩展名清单（Downloader.looksLikeFileUrl），普通网页链接不命中。
                        // fork.14~16 的老做法是「主界面留在弹窗之下」：分诊后照常组合 BrowserScreen、
                        // 弹窗叠进主界面同一个任务，真机三版用户都只见浏览器界面、没见到弹窗。
                        // 本版改 ABDM 式：弹窗有独立 taskAffinity、拉起带 NEW_TASK 落在自己的任务里
                        // 压在来源应用之上；主界面转交成功即置 externalHandled 走既有退场收尾
                        // （冷启 onResume 后 finish、已运行 moveTaskToBack 不带自己到前台），且冷启
                        // 时不再 setContent——浏览器界面全程不出现。拉起失败才落回浏览器打开。
                        // 弹窗探测发现是网页时带 EXTRA_OPEN_IN_BROWSER 回来，这类意图不再重复
                        // 拦截（防循环）。
                        val fromDialogEscape = intent.getBooleanExtra(EXTRA_OPEN_IN_BROWSER, false)
                        val fileLike = Downloader.looksLikeFileUrl(url)
                        // fork.15 步进：外部分诊的输入与判定结果都落盘（规则结果见 LinkRouter 日志），
                        // 真机再出问题时能直接读出链接在哪一环被放行进了浏览器
                        Diag.log("MainActivity triage: host=${data.host} fileLike=$fileLike escape=$fromDialogEscape")
                        if (!fromDialogEscape && fileLike) {
                            Diag.log("MainActivity: file-like external url -> download dialog (trampoline): ${url.take(100)}")
                            val launched = runCatching {
                                startActivity(DownloadDialogActivity.intent(this, url))
                            }.isSuccess
                            Diag.log("MainActivity: download dialog trampoline launched=$launched")
                            if (launched) {
                                downloadTrampoline = true
                                externalHandled = true
                            } else {
                                incomingUrl.value = url
                            }
                        } else {
                            incomingUrl.value = url
                        }
                    }
                }
            }
        }
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
     * 规则送出的收尾统一放在 onResume：送出发生在 onCreate / onNewIntent，当场 finish /
     * moveTaskToBack 的时序曾让外部表现异常，推迟到 Activity 真正到前台后再退场。
     * （fork.17 起下载跳板转交也走同一收尾：弹窗在独立任务里，finish / moveTaskToBack
     * 只作用于主界面自己的任务，碰不到弹窗。）
     */
    override fun onResume() {
        super.onResume()
        if (retreatAfterResume) {
            retreatAfterResume = false
            externalHandled = false
            if (coldExternal) {
                coldExternal = false
                finish()
            } else {
                moveTaskToBack(true)
            }
        }
    }

    /** 图标切换推迟到离开应用时再生效，避免正在前台的入口被停用导致界面被系统关掉 */
    override fun onStop() {
        super.onStop()
        // 隐私模式：切后台 / 锁屏自动退回普通模式（下载任务跑在独立服务里，不受影响）
        PrivacyMode.exit()
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
        dev.dsh.miuixbrowser.Diag.log("MainActivity.onCreate intent: action=${intent.action} data=${intent.data}")
        AdBlock.init(this)
        CacheCleaner.runOnColdStart(this)
        // 下载任务落盘恢复（杀进程/重启后未完成任务可真续传）+ 链接分发引擎初始化。
        // fork.6：必须赶在下面 consumeViewIntent 分诊之前——fork.5 时 init 在分诊之后，
        // 冷启动 match() 因未初始化恒返回 null，规则判定与回退链在冷启动下全程静默失效。
        Downloader.restore(this)
        LinkDispatch.init(this)
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
                retreatAfterResume = true
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

        if (downloadTrampoline) {
            // fork.17 跳板冷启：直链已转交独立任务的下载弹窗，主界面到此为止——不请求
            // 通知权限、不 setContent（BrowserScreen 不组合、主页与标签都不加载），
            // onResume 收尾时 finish，屏幕上只留下载弹窗压在来源应用之上。
            Diag.log("MainActivity: download trampoline launch, skip browser UI")
        } else {
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
    }

    /** 把未捕获异常写到应用外部目录，方便没有 adb 时排查。 */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val dir = externalMediaDirs.firstOrNull() ?: getExternalFilesDir(null) ?: filesDir
                dir.mkdirs()
                val file = java.io.File(dir, "crash.txt")
                file.writeText(
                    buildString {
                        appendLine("time=" + java.util.Date())
                        appendLine("thread=" + thread.name)
                        appendLine(error.toString())
                        error.stackTraceToString().lines().take(80).forEach { appendLine(it) }
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
