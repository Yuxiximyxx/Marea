package dev.dsh.miuixbrowser

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.annotation.DrawableRes

/** 可选的桌面图标。下标与 [AppSettings.appIcon] 对应，alias 名与 AndroidManifest 里的 activity-alias 一致。 */
data class AppIconOption(
    val label: String,
    val alias: String,
    @DrawableRes val preview: Int,
)

object AppIconManager {

    val ICONS = listOf(
        AppIconOption("默认", "IconDefault", R.drawable.icon_preview_default),
        AppIconOption("多彩", "IconColorful", R.drawable.icon_preview_colorful),
        AppIconOption("立体", "IconSolid", R.drawable.icon_preview_solid3d),
        AppIconOption("深色", "IconDark", R.drawable.icon_preview_dark),
    )

    /** 夜间别名（fork.5）：不在手动列表里，只在图标选「默认」时按系统日夜自动启停 */
    const val NIGHT_ALIAS = "IconNight"

    /** 启用选中的 alias、停用其余的。DONT_KILL_APP 避免系统直接杀进程。 */
    fun apply(context: Context, index: Int) {
        val pm = context.packageManager
        // 手动选了图标（含选回「默认」之外的任何一个）时，夜间自动别名必须关掉，不能和手动图标同时挂在桌面
        setState(pm, context, NIGHT_ALIAS, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        val target = index.coerceIn(0, ICONS.lastIndex)
        // 先启用目标再停用其他，保证任何时刻桌面上至少有一个入口
        ICONS.forEachIndexed { i, icon ->
            if (i == target) setState(pm, context, icon.alias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        }
        ICONS.forEachIndexed { i, icon ->
            if (i != target) setState(pm, context, icon.alias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        }
    }

    /**
     * 「默认」图标的日夜自动切换（fork.5，与 FluxTie / LumaGA 同一套定版机制）：
     * 系统夜间 → 启用 IconNight、停用 IconDefault；白天反之。
     * 只在图标选「默认」（appIcon == 0）时由 MainActivity 调用；带状态去重，重复调用不惊动启动器。
     */
    fun applyAutoDayNight(context: Context) {
        val pm = context.packageManager
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val enableAlias = if (night) NIGHT_ALIAS else "IconDefault"
        val disableAlias = if (night) "IconDefault" else NIGHT_ALIAS
        val already = pm.getComponentEnabledSetting(component(context, enableAlias)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED &&
            pm.getComponentEnabledSetting(component(context, disableAlias)) !=
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        if (already) return
        // 先启用目标再停用另一个，保证任何时刻桌面上至少有一个入口
        setState(pm, context, enableAlias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        setState(pm, context, disableAlias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    /** 实际生效的是哪一个（以系统组件状态为准）；没有任何显式启用时为默认。 */
    fun current(context: Context): Int {
        val pm = context.packageManager
        val idx = ICONS.indexOfFirst { icon ->
            pm.getComponentEnabledSetting(component(context, icon.alias)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        return if (idx >= 0) idx else 0
    }

    // alias 在清单里以 ".Xxx" 声明，类名相对 namespace（dev.dsh.miuixbrowser）；
    // applicationId 已改为 io.github.yuxiximyxx.marea 也不影响：包名一侧用 context.packageName 动态取
    private fun component(context: Context, alias: String) =
        ComponentName(context.packageName, "dev.dsh.miuixbrowser.$alias")

    private fun setState(pm: PackageManager, context: Context, alias: String, state: Int) {
        runCatching {
            pm.setComponentEnabledSetting(component(context, alias), state, PackageManager.DONT_KILL_APP)
        }
    }
}
