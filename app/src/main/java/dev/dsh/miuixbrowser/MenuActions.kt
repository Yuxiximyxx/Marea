package dev.dsh.miuixbrowser

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.WorldClock
import top.yukonga.miuix.kmp.icon.glass.Translate
import top.yukonga.miuix.kmp.icon.glass.Sidebar
import top.yukonga.miuix.kmp.icon.glass.Share
import top.yukonga.miuix.kmp.icon.glass.Settings
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.icon.glass.Refresh
import top.yukonga.miuix.kmp.icon.glass.Link
import top.yukonga.miuix.kmp.icon.glass.Hide
import top.yukonga.miuix.kmp.icon.glass.GridView
import top.yukonga.miuix.kmp.icon.glass.Favorites
import top.yukonga.miuix.kmp.icon.glass.Edit
import top.yukonga.miuix.kmp.icon.glass.File
import top.yukonga.miuix.kmp.icon.glass.Download
import top.yukonga.miuix.kmp.icon.glass.Copy
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Add

enum class MenuAction(val id: String, val label: String, val icon: ImageVector) {
    NewTab("new_tab", "新建标签", MiuixIcons.Glass.Add),
    Incognito("incognito", "无痕浏览", MiuixIcons.Glass.Hide),
    Desktop("desktop", "桌面版", MiuixIcons.Glass.GridView),
    Find("find", "页内查找", MiuixIcons.Glass.Search),
    Translate("translate", "翻译", MiuixIcons.Glass.Translate),
    Favorite("favorite", "收藏", MiuixIcons.Glass.Favorites),
    Share("share", "分享", MiuixIcons.Glass.Share),
    Refresh("refresh", "刷新", MiuixIcons.Glass.Refresh),
    CopyLink("copy", "复制链接", MiuixIcons.Glass.Copy),
    External("external", "外部打开", MiuixIcons.Glass.Link),
    Settings("settings", "设置", MiuixIcons.Glass.Settings),
    Home("home", "主页", MiuixIcons.Glass.Sidebar),
    Downloads("downloads", "下载", MiuixIcons.Glass.Download),
    Extensions("extensions", "脚本", MiuixIcons.Glass.Edit),
    ScriptMenu("script_menu", "脚本命令", MiuixIcons.Glass.File),
    History("history", "历史", MiuixIcons.Glass.WorldClock),
    CloseTab("close", "关闭标签", MiuixIcons.Glass.Close),
}

/** 更多面板的图标顺序，可拖动排序并持久化 */
class MenuOrderStore(context: Context) {

    private val prefs = context.getSharedPreferences("mafari_menu", Context.MODE_PRIVATE)

    val order = mutableStateListOf<MenuAction>()

    init {
        val saved = prefs.getString(KEY, null)
        val ids = saved?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        val restored = ids.mapNotNull { id -> MenuAction.entries.firstOrNull { it.id == id } }
        order.addAll(restored)
        MenuAction.entries.forEach { if (it !in order) order.add(it) }
    }

    fun move(from: Int, to: Int) {
        if (from !in order.indices) return
        val target = to.coerceIn(0, order.lastIndex)
        if (from == target) return
        val item = order.removeAt(from)
        order.add(target, item)
        persist()
    }

    private fun persist() {
        prefs.edit().putString(KEY, order.joinToString(",") { it.id }).apply()
    }

    private companion object {
        const val KEY = "order"
    }
}
