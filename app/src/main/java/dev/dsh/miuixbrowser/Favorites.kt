package dev.dsh.miuixbrowser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class Favorite(val name: String, val url: String)

/** 主页收藏（书签）的增删改 + 本地持久化。 */
class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("mafari", Context.MODE_PRIVATE)

    val items = mutableStateListOf<Favorite>().apply {
        addAll(load())
    }

    private fun load(): List<Favorite> {
        val raw = prefs.getString(KEY, null) ?: return defaults()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(Favorite(obj.optString("name"), obj.optString("url")))
                }
            }
        }.getOrDefault(defaults())
    }

    private fun defaults(): List<Favorite> = listOf(
        Favorite("百度", "https://www.baidu.com"),
        Favorite("知乎", "https://www.zhihu.com"),
        Favorite("B站", "https://www.bilibili.com"),
        Favorite("GitHub", "https://github.com"),
        Favorite("微博", "https://weibo.com"),
        Favorite("淘宝", "https://www.taobao.com"),
    )

    fun add(name: String, url: String) {
        if (url.isBlank()) return
        val fixed = toUrl(url)
        items.add(Favorite(name.ifBlank { hostOf(fixed) }, fixed))
        persist()
    }

    fun update(index: Int, name: String, url: String) {
        if (index !in items.indices || url.isBlank()) return
        val fixed = toUrl(url)
        items[index] = Favorite(name.ifBlank { hostOf(fixed) }, fixed)
        persist()
    }

    fun remove(index: Int) {
        if (index in items.indices) {
            items.removeAt(index)
            persist()
        }
    }

    fun reset() {
        items.clear()
        items.addAll(defaults())
        persist()
    }

    private fun persist() {
        val array = JSONArray()
        items.forEach {
            array.put(JSONObject().put("name", it.name).put("url", it.url))
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "favorites"
    }
}
