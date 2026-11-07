package dev.dsh.miuixbrowser

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * 隐私报告里「频繁获取敏感权限的网站」的数据源：记录每次网页发起的摄像头 / 麦克风 / 定位请求
 * （无论用户允许还是拒绝，记的是「请求」本身），按主机统计近 24 小时的次数。
 *
 * 只存主机名、请求类别和时间，最多保留 200 条、7 天；无痕标签页不记录（调用方负责）。
 */
object PrivacyStats {

    private data class PermEvent(val host: String, val kinds: List<String>, val time: Long)

    /** 一个被提醒的网站：近 [WINDOW_MS] 内请求次数 ≥ [THRESHOLD]。 */
    class Frequent(val host: String, val count: Int, val kinds: List<String>)

    const val WINDOW_MS = 24L * 60 * 60 * 1000
    const val THRESHOLD = 3
    private const val KEEP_MS = 7L * 24 * 60 * 60 * 1000
    private const val MAX_EVENTS = 200
    private const val PREFS = "mafari_privacy"
    private const val KEY = "perm_events"

    private val events = mutableStateListOf<PermEvent>()
    private var loaded = false

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            val cutoff = System.currentTimeMillis() - KEEP_MS
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val t = o.optLong("t")
                if (t < cutoff) continue
                val ks = o.optJSONArray("k")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                events.add(PermEvent(o.optString("h"), ks, t))
            }
        }
    }

    /** what 是界面文案（「使用摄像头、麦克风」「获取你的位置」），这里归一成类别。 */
    fun recordPermission(context: Context, host: String, what: String) {
        val h = host.trim().lowercase()
        if (h.isEmpty()) return
        val kinds = buildList {
            if ("摄像头" in what) add("摄像头")
            if ("麦克风" in what) add("麦克风")
            if ("位置" in what || "定位" in what) add("定位")
        }
        if (kinds.isEmpty()) return
        ensureLoaded(context)
        events.add(PermEvent(h, kinds, System.currentTimeMillis()))
        val cutoff = System.currentTimeMillis() - KEEP_MS
        events.removeAll { it.time < cutoff }
        while (events.size > MAX_EVENTS) events.removeAt(0)
        val arr = JSONArray()
        events.forEach { e ->
            arr.put(JSONObject().put("h", e.host).put("t", e.time).put("k", JSONArray(e.kinds)))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    /** 近 24 小时请求次数 ≥ 阈值的网站，次数多的在前；读的是 Compose 状态，列表变化会触发重组。 */
    fun frequent(context: Context): List<Frequent> {
        ensureLoaded(context)
        val cutoff = System.currentTimeMillis() - WINDOW_MS
        return events.filter { it.time >= cutoff }
            .groupBy { it.host }
            .filter { it.value.size >= THRESHOLD }
            .map { (host, list) -> Frequent(host, list.size, list.flatMap { it.kinds }.distinct()) }
            .sortedByDescending { it.count }
    }
}
