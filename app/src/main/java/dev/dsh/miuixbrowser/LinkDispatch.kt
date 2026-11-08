package dev.dsh.miuixbrowser

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** 分发作用域：链接点击 / 下载触发。本期只落这两个拦截点。 */
enum class DispatchScope { CLICK, DOWNLOAD }

/**
 * 一条分发规则（LinkGo 式）。列表顺序即优先级，首中即停。
 * matchType：0 = 正则整行匹配，1 = 域名（含子域名），2 = 链接包含子串。
 * action：0 = ACTION_VIEW 打开，1 = ACTION_SEND 以文本分享送出。
 * rewrite 为改写模板：正则模式下支持 $1 / ${name}（也接受 $<name> 写法）；域名/包含模式下 $0 表示原链接。
 * targetActivity 留空 = 只指定包名、由系统在目标应用内路由；targetPackage 留空 = 完全交给系统选择器。
 */
data class DispatchRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val priority: Int = 0,
    val scopes: Set<DispatchScope> = setOf(DispatchScope.DOWNLOAD),
    val matchType: Int = 0,
    val pattern: String = "",
    val rewrite: String = "",
    val targetPackage: String = "",
    val targetActivity: String = "",
    val action: Int = 0,
    val confirm: Boolean = false,
    val builtin: Boolean = false,
)

/**
 * 链接分发引擎：规则落盘在 SharedPreferences（JSON），首次启动播种预置规则包。
 * 预置包只有一条「网盘 → 云析」：内置下载器的定位是自己接管下载，
 * GitHub 等下载链接不再预置外跳规则（用户仍可自建任意规则）。
 */
object LinkDispatch {
    private const val PREFS = "mafari_dispatch"
    private const val KEY_STATE = "state"

    var enabled by mutableStateOf(true)
        private set

    val rules = mutableStateListOf<DispatchRule>()

    @Volatile
    private var inited = false
    private var appContext: Context? = null
    private val patternCache = HashMap<String, Pattern?>()

    const val YUNX_PACKAGE = "com.yunx.app"

    /** 网盘分享链接（云析支持面）：夸克 / UC / 迅雷 / 百度 / 139 / 123 / 115 / 光鸭 / 蓝奏云（(?i) 容忍粘贴来的大写形态） */
    const val PAN_PATTERN =
        "(?i)https?://(pan\\.quark\\.cn/s/.+|drive\\.uc\\.cn/s/.+|pan\\.xunlei\\.com/s/.+" +
            "|pan\\.baidu\\.com/(s/|share/).+|(yun|caiyun)\\.139\\.com/.+" +
            "|([a-z0-9-]+\\.)*123pan\\.(com|cn)/(s/|123pan/).+|115\\.com/s/.+" +
            "|(www\\.)?guangyapan\\.com/s/.+|([a-z0-9-]+\\.)?lanzou[a-z0-9]*\\.com/.+" +
            "|(www\\.)?lanzn\\.com/.+|(www\\.)?lanrar\\.com/.+)"

    @Synchronized
    fun init(context: Context) {
        if (inited) return
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STATE, null)
        if (raw == null) {
            enabled = true
            rules.clear()
            rules.add(presetPanRule())
            saveLocked()
        } else {
            runCatching {
                val root = JSONObject(raw)
                enabled = root.optBoolean("enabled", true)
                val arr = root.optJSONArray("rules") ?: JSONArray()
                val loaded = ArrayList<DispatchRule>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val scopesArr = o.optJSONArray("scopes") ?: JSONArray()
                    val scopes = HashSet<DispatchScope>()
                    for (j in 0 until scopesArr.length()) {
                        runCatching { scopes.add(DispatchScope.valueOf(scopesArr.optString(j))) }
                    }
                    loaded.add(
                        DispatchRule(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = o.optString("name"),
                            enabled = o.optBoolean("enabled", true),
                            priority = o.optInt("priority", i),
                            scopes = scopes.ifEmpty { setOf(DispatchScope.DOWNLOAD) },
                            matchType = o.optInt("matchType", 0),
                            pattern = o.optString("pattern"),
                            rewrite = o.optString("rewrite"),
                            targetPackage = o.optString("pkg"),
                            targetActivity = o.optString("act"),
                            action = o.optInt("action", 0),
                            confirm = o.optBoolean("confirm", false),
                            builtin = o.optBoolean("builtin", false),
                        ),
                    )
                }
                rules.clear()
                rules.addAll(loaded.sortedBy { it.priority })
            }
        }
        // 内置规则的匹配模式随版本刷新：已装机用户落盘的是旧模式串，覆盖不到的新形态（如蓝奏裸域）
        // 靠这里把 builtin 规则的 pattern 同步到代码里的最新版；用户对规则的启停/作用域/确认等编辑保留不动
        var migrated = false
        for (i in rules.indices) {
            val r = rules[i]
            if (r.builtin && r.targetPackage == YUNX_PACKAGE && r.pattern != PAN_PATTERN) {
                rules[i] = r.copy(pattern = PAN_PATTERN)
                migrated = true
            }
        }
        if (migrated) saveLocked()
        inited = true
    }

    private fun presetPanRule(): DispatchRule = DispatchRule(
        name = "网盘 → 云析",
        enabled = true,
        priority = 0,
        scopes = setOf(DispatchScope.CLICK, DispatchScope.DOWNLOAD),
        matchType = 0,
        pattern = PAN_PATTERN,
        targetPackage = YUNX_PACKAGE,
        targetActivity = "",
        action = 0,
        confirm = false,
        builtin = true,
    )

    private fun saveLocked() {
        val ctx = appContext ?: return
        val arr = JSONArray()
        rules.forEachIndexed { index, r ->
            val scopesArr = JSONArray()
            r.scopes.forEach { scopesArr.put(it.name) }
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("name", r.name)
                    .put("enabled", r.enabled)
                    .put("priority", index)
                    .put("scopes", scopesArr)
                    .put("matchType", r.matchType)
                    .put("pattern", r.pattern)
                    .put("rewrite", r.rewrite)
                    .put("pkg", r.targetPackage)
                    .put("act", r.targetActivity)
                    .put("action", r.action)
                    .put("confirm", r.confirm)
                    .put("builtin", r.builtin),
            )
        }
        val root = JSONObject().put("enabled", enabled).put("rules", arr)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_STATE, root.toString()).apply()
    }

    @Synchronized
    fun save() = saveLocked()

    fun updateEnabled(value: Boolean) {
        enabled = value
        save()
    }

    fun upsert(rule: DispatchRule) {
        val idx = rules.indexOfFirst { it.id == rule.id }
        if (idx >= 0) rules[idx] = rule else rules.add(rule)
        save()
    }

    fun remove(rule: DispatchRule) {
        rules.removeAll { it.id == rule.id }
        save()
    }

    fun move(rule: DispatchRule, dir: Int) {
        val idx = rules.indexOfFirst { it.id == rule.id }
        val target = idx + dir
        if (idx < 0 || target < 0 || target >= rules.size) return
        val item = rules.removeAt(idx)
        rules.add(target, item)
        save()
    }

    private fun compile(pattern: String): Pattern? {
        if (patternCache.containsKey(pattern)) return patternCache[pattern]
        val compiled = try {
            Pattern.compile(pattern)
        } catch (e: PatternSyntaxException) {
            null
        }
        if (patternCache.size > 64) patternCache.clear()
        patternCache[pattern] = compiled
        return compiled
    }

    /** 正则语法校验（编辑器用）：合法返回 null，否则返回错误信息 */
    fun patternError(rule: DispatchRule): String? {
        if (rule.matchType != 0) return null
        if (rule.pattern.isBlank()) return "模式为空"
        return try {
            Pattern.compile(rule.pattern)
            null
        } catch (e: PatternSyntaxException) {
            "正则语法错误：" + (e.description ?: e.message ?: "")
        }
    }

    private val namedRef = Regex("""\$<([A-Za-z][A-Za-z0-9_]*)>""")
    private val schemeRef = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*:""")

    /** 规则是否命中 [url]；命中返回改写后的最终链接，未命中返回 null */
    fun applyMatch(rule: DispatchRule, url: String): String? {
        if (rule.pattern.isBlank()) return null
        return when (rule.matchType) {
            0 -> {
                val p = compile(rule.pattern) ?: return null
                val m = p.matcher(url)
                if (!m.matches()) return null
                if (rule.rewrite.isBlank()) return url
                // $<name> 转成 Java replacement 的 ${name}；$1 原生支持
                val template = namedRef.replace(rule.rewrite) { "\${" + it.groupValues[1] + "}" }
                val out = runCatching { m.replaceAll(template) }.getOrNull() ?: return url
                if (schemeRef.containsMatchIn(out)) out else url
            }
            1 -> {
                val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return null
                val domain = rule.pattern.trim().lowercase()
                    .substringAfter("://").substringBefore('/').substringBefore(':')
                if (domain.isBlank()) return null
                if (host != domain && !host.endsWith(".$domain")) return null
                applyTemplate(rule, url)
            }
            else -> {
                if (!url.contains(rule.pattern.trim(), ignoreCase = true)) return null
                applyTemplate(rule, url)
            }
        }
    }

    private fun applyTemplate(rule: DispatchRule, url: String): String {
        if (rule.rewrite.isBlank()) return url
        val out = rule.rewrite.replace("\$0", url)
        return if (schemeRef.containsMatchIn(out)) out else url
    }

    /** 在 [scope] 下按优先级匹配，首中即停；返回 (规则, 最终链接) */
    fun match(url: String, scope: DispatchScope): Pair<DispatchRule, String>? {
        if (!enabled || !inited) return null
        for (rule in rules) {
            if (!rule.enabled || scope !in rule.scopes) continue
            val out = applyMatch(rule, url) ?: continue
            return rule to out
        }
        return null
    }

    /** 把 [url] 按规则送到目标应用；成功返回 true，失败（未装/无组件）返回 false 由调用方回退 */
    fun dispatch(context: Context, rule: DispatchRule, url: String): Boolean {
        val intent = if (rule.action == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }
        val pkg = rule.targetPackage.trim()
        val act = rule.targetActivity.trim()
        if (pkg.isNotBlank()) {
            if (act.isNotBlank()) {
                val cls = if (act.startsWith(".")) pkg + act else act
                intent.component = ComponentName(pkg, cls)
            } else {
                intent.setPackage(pkg)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun enabledRuleCount(): Int = rules.count { it.enabled }
}
