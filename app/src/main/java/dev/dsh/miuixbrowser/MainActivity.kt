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

    /** 本次外部链接是否已被分诊处理（规则送出 / 下载弹窗），已处理则不进浏览器主界面 */
    private var externalHandled = false

    /**
     * 分诊判定要开下载弹窗的链接（fork.6）：不在 onCreate / onNewIntent 里当场拉起，
     * 等 onResume（Activity 真正到前台）再启动，避免冷启动 finish 紧跟 startActivity
     * 把弹窗启动吞掉（fork.5 的「点了没反应」）。
     */
    private var pendingDialogUrl: String? = null

    /** 本次外部链接已处理完，onResume 后收尾：冷启动 finish、已运行 moveTaskToBack */
    private var retreatAfterResume = false

    /** 本次处理发生在冷启动（onCreate）路径，收尾用 finish 而不是 moveTaskToBack */
    private var coldExternal = false

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"
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
            // 收尾推迟到 onResume 统一做（见 onResume 注释）：先把弹窗真正拉起来再退后台，
            // 顺序反过来弹窗会被一起退到后台、外部看就是没反应
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
                // fork.5 分诊（fork.6 改为判定与启动分离）：规则送出 / 直链下载弹窗命中时不进浏览器（见 LinkRouter）
                val url = data.toString()
                when (val r = LinkRouter.routeExternal(this, url)) {
                    is LinkRouter.Result.Dialog -> {
                        pendingDialogUrl = r.url
                        externalHandled = true
                    }
                    LinkRouter.Result.Dispatched -> externalHandled = true
                    LinkRouter.Result.Unhandled -> incomingUrl.value = url
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
     * 外部链接分诊的收尾统一放在 onResume（fork.6）：此时 Activity 已真正到前台，
     * 从这里 startActivity 拉下载弹窗不会被后台启动限制吞掉；fork.5 在 onCreate 里
     * startActivity 后立即 finish、或在 onNewIntent 里启动后立即 moveTaskToBack，
     * 冷启动 / 任务退后台的时序都会让弹窗无声消失且链接丢失。
     * 弹窗拉起失败 → 回退成进浏览器打开该链接，保证任何路径都有可见结果。
     */
    override fun onResume() {
        super.onResume()
        val dialogUrl = pendingDialogUrl
        if (dialogUrl != null) {
            pendingDialogUrl = null
            retreatAfterResume = false
            externalHandled = false
            Diag.log("LinkRouter: about to launch dialog url=$dialogUrl cold=$coldExternal")
            val launched = runCatching {
                startActivity(DownloadDialogActivity.intent(this, dialogUrl))
            }.isSuccess
            Diag.log("LinkRouter: dialog launch -> $launched url=${dialogUrl.take(80)}")
            if (launched) {
                if (coldExternal) finish() else moveTaskToBack(true)
            } else {
                // 弹窗没拉起来：不许静默吞掉，回退到浏览器里打开这条链接
                incomingUrl.value = dialogUrl
            }
            coldExternal = false
            return
        }
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
