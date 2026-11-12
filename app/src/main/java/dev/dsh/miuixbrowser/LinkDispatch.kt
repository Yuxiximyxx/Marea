package dev.dsh.miuixbrowser

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** 分发作用域：链接点击 / 下载触发。长按菜单分发（fork.20）复用 CLICK 作用域判定。 */
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
    /** 短链预解析（fork.20）：判定前先跟随重定向展开短链，再用展开后的地址匹配与送出 */
    val resolveShortLink: Boolean = false,
    /**
     * 提取正则（fork.20，LinkGo extractPattern 真语义）：仅正则模式有意义——
     * pattern 只作入口门槛，命中后用它在链接里 find 提取，提取串作工作链接、捕获组供模板引用
     */
    val extractPattern: String = "",
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
                    loaded.add(ruleFromJson(o, i))
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

    /** 落盘 JSON → 规则（启动加载与 Marea 自有格式导入共用同一条解析路径） */
    private fun ruleFromJson(o: JSONObject, fallbackPriority: Int): DispatchRule {
        val scopesArr = o.optJSONArray("scopes") ?: JSONArray()
        val scopes = HashSet<DispatchScope>()
        for (j in 0 until scopesArr.length()) {
            runCatching { scopes.add(DispatchScope.valueOf(scopesArr.optString(j))) }
        }
        return DispatchRule(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name"),
            enabled = o.optBoolean("enabled", true),
            priority = o.optInt("priority", fallbackPriority),
            scopes = scopes.ifEmpty { setOf(DispatchScope.DOWNLOAD) },
            matchType = o.optInt("matchType", 0),
            pattern = o.optString("pattern"),
            rewrite = o.optString("rewrite"),
            targetPackage = o.optString("pkg"),
            targetActivity = o.optString("act"),
            action = o.optInt("action", 0),
            confirm = o.optBoolean("confirm", false),
            builtin = o.optBoolean("builtin", false),
            resolveShortLink = o.optBoolean("resolve", false),
            extractPattern = o.optString("extract"),
        )
    }

    // ---------- 规则导入（LinkGo 导出格式 / Marea 自有格式） ----------

    data class ImportDegradation(val name: String, val reason: String)

    data class ImportReport(
        /** 识别到的来源格式："LinkGo" / "Marea"；解析失败时为空串且 error 非空 */
        val format: String,
        /** 文件中的规则总数 */
        val total: Int,
        /** 实际并入的条数（追加在现有列表末尾） */
        val added: Int,
        /** 有语义降级的条目及逐条原因 */
        val degraded: List<ImportDegradation>,
        /** 携带 LinkGo 专有字段（预热/启动模式/最近任务排除等非默认值）的条数：整批丢弃、只汇总计数 */
        val proprietaryDropped: Int,
        val error: String? = null,
    )

    /**
     * 从 JSON 文本导入规则：自动识别 LinkGo 导出格式（顶层 linkgo_rules 数组）与
     * Marea 自有格式（落盘同构的 rules 数组，或裸规则数组）。导入条目按文件顺序
     * 追加到现有列表末尾，既有规则与预置规则不动；LinkGo 规则统一给 CLICK 作用域
     * （LinkGo 的拦截语义就是「发现链接时送出」，没有下载触发这个面）。
     */
    @Synchronized
    fun importFromJson(text: String): ImportReport {
        val trimmed = text.trim()
        var format = ""
        var items: JSONArray? = null
        try {
            if (trimmed.startsWith("[")) {
                items = JSONArray(trimmed)
                format = "Marea"
            } else {
                val root = JSONObject(trimmed)
                when {
                    root.has("linkgo_rules") -> {
                        items = root.optJSONArray("linkgo_rules")
                        format = "LinkGo"
                    }
                    root.has("rules") -> {
                        items = root.optJSONArray("rules")
                        format = "Marea"
                    }
                }
            }
        } catch (e: Exception) {
            return ImportReport("", 0, 0, emptyList(), 0, "JSON 解析失败：${e.message}")
        }
        val arr = items ?: return ImportReport("", 0, 0, emptyList(), 0, "无法识别的文件格式（既不是 LinkGo 导出，也不是 Marea 规则文件）")
        val converted = ArrayList<DispatchRule>()
        val degraded = ArrayList<ImportDegradation>()
        var proprietary = 0
        val basePriority = rules.size
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (format == "LinkGo") {
                val (rule, reasons, hasProprietary) = convertLinkGoRule(o, basePriority + converted.size)
                converted.add(rule)
                if (reasons.isNotEmpty()) degraded.add(ImportDegradation(rule.name, reasons.joinToString("；")))
                if (hasProprietary) proprietary++
            } else {
                converted.add(ruleFromJson(o, basePriority + converted.size))
            }
        }
        if (converted.isEmpty()) {
            return ImportReport(format, arr.length(), 0, emptyList(), 0, "文件里没有可导入的规则")
        }
        rules.addAll(converted)
        saveLocked()
        return ImportReport(format, arr.length(), converted.size, degraded, proprietary)
    }

    /**
     * LinkGo 规则 → Marea 规则。返回 (规则, 降级原因列表, 是否携带被丢弃的专有字段)。
     * 关键语义对齐：
     * - LinkGo 正则是部分匹配（find），Marea 引擎是整行 matches()：pattern 包成
     *   .*(?:…).* 的整行等价式（非捕获组包裹，原捕获组编号不变，模板 $n 不受影响；
     *   整行式下 $0 恰好等于完整链接，与 LinkGo 的 {url} 同义）。
     * - extractPattern（fork.20 起落真语义）：pattern 只作入口门槛，extractPattern 原样存下，
     *   命中后由引擎在链接里 find 提取工作链接、其捕获组供模板引用（见 applyMatch 的提取支路）。
     * - 短链预解析（fork.20 起落地）：resolveShortLink / resolveStrategy 落进规则字段，不再丢弃。
     * - 模板占位符：{url}→$0、{n}→$n；带变换后缀的（{1_url_dec}、{url_b64_enc} 等）
     *   Marea 引擎暂不支持，退化为对应分组并记入降级原因。
     */
    private fun convertLinkGoRule(o: JSONObject, priority: Int): Triple<DispatchRule, List<String>, Boolean> {
        val reasons = ArrayList<String>()
        val name = o.optString("name").ifBlank { "未命名规则" }
        val enabled = o.optBoolean("isEnabled", true)
        val patternRaw = o.optString("pattern")
        val extract = o.optString("extractPattern")
        val templateRaw = if (o.has("template")) o.optString("template") else ""
        // targetPackage 是 "包名/类名" 合写：按第一个 / 拆开；只有包名则类名留空；空串 = 系统选择
        val targetRaw = o.optString("targetPackage").trim()
        val slash = targetRaw.indexOf('/')
        val pkg = if (slash >= 0) targetRaw.substring(0, slash).trim() else targetRaw
        val act = if (slash >= 0) targetRaw.substring(slash + 1).trim() else ""

        // 模板占位符转换（变换类先处理并记录，再处理普通 {url}/{n}）
        var badTransform: String? = null
        var rewrite = templateRaw
        if (rewrite.isNotBlank()) {
            rewrite = TRANSFORM_PLACEHOLDER.replace(rewrite) { m ->
                badTransform = m.value
                if (m.groupValues[1] == "url") "\$0" else "\$" + m.groupValues[1]
            }
            rewrite = PLAIN_PLACEHOLDER.replace(rewrite) { m ->
                if (m.groupValues[1] == "url") "\$0" else "\$" + m.groupValues[1]
            }
        }
        if (badTransform != null) {
            reasons.add("模板含暂不支持的变换 $badTransform，已降级为对应分组原文")
        }

        val matchType: Int
        val pattern: String
        // fork.20：extractPattern 不再冒充匹配正则——pattern 作门槛（整行等价式），
        // extractPattern 原样存进规则字段，提取在命中后发生。pattern 缺失时以 extract
        // 的整行式作门槛（提得到必然过得了门槛，语义等价）。
        if (extract.isNotBlank()) {
            matchType = 0
            pattern = wholeLine(patternRaw.ifBlank { extract })
        } else if (o.optString("matchType") == "CONTAINS") {
            matchType = 2
            pattern = patternRaw
        } else {
            matchType = 0
            pattern = wholeLine(patternRaw)
        }

        // 短链预解析（fork.20 落地）：resolveShortLink 或 resolveStrategy 非 NONE 都记为开启。
        // DIRECT / RE_DISPATCH 的差别由引擎统一消化：展开后每条规则先测展开地址再测原地址
        // （见 matchAfterResolve），DIRECT 规则吃展开地址、RE_DISPATCH 的后续规则也能对展开地址重走。
        val strategy = o.optString("resolveStrategy")
        val resolveShort = o.optBoolean("resolveShortLink", false) ||
            (strategy.isNotBlank() && strategy != "NONE")
        // LinkGo 专有字段：预热、启动模式、最近任务排除等，Marea 无对应概念，整批丢弃只计数
        val hasProprietary = o.optBoolean("isPreheatEnabled", false) ||
            o.optBoolean("excludeFromRecents", false) ||
            (o.has("ruleLaunchMode") && o.optInt("ruleLaunchMode", -1) != -1)

        val rule = DispatchRule(
            name = name,
            enabled = enabled,
            priority = priority,
            scopes = setOf(DispatchScope.CLICK),
            matchType = matchType,
            pattern = pattern,
            rewrite = rewrite,
            targetPackage = pkg,
            targetActivity = act,
            action = 0,
            confirm = false,
            builtin = false,
            resolveShortLink = resolveShort,
            extractPattern = extract,
        )
        return Triple(rule, reasons, hasProprietary)
    }

    /** 部分匹配正则 → 整行等价式：非捕获组包裹，原捕获组编号与 $0 语义都不变 */
    private fun wholeLine(partial: String): String = ".*(?:$partial).*"

    private val TRANSFORM_PLACEHOLDER = Regex("""\{(url|\d+)((?:_[a-zA-Z0-9]+)+)\}""")
    private val PLAIN_PLACEHOLDER = Regex("""\{(url|\d+)\}""")

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
                    .put("builtin", r.builtin)
                    .put("resolve", r.resolveShortLink)
                    .put("extract", r.extractPattern),
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

    /** 正则语法校验（编辑器用）：合法返回 null，否则返回错误信息；提取正则一并校验 */
    fun patternError(rule: DispatchRule): String? {
        if (rule.matchType != 0) return null
        if (rule.pattern.isBlank()) return "模式为空"
        try {
            Pattern.compile(rule.pattern)
        } catch (e: PatternSyntaxException) {
            return "正则语法错误：" + (e.description ?: e.message ?: "")
        }
        if (rule.extractPattern.isNotBlank()) {
            try {
                Pattern.compile(rule.extractPattern)
            } catch (e: PatternSyntaxException) {
                return "提取正则语法错误：" + (e.description ?: e.message ?: "")
            }
        }
        return null
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
                // fork.20：extractPattern 真语义——上面的 pattern 只作门槛，命中后走提取支路。
                // 无 extractPattern 的规则走下方原路径，行为与旧版一致。
                if (rule.extractPattern.isNotBlank()) return applyExtract(rule, url)
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

    /**
     * extractPattern 提取支路（门槛已在 [applyMatch] 里过）：用 extractPattern 在链接里
     * find 提取——提取串（group 0）成为工作链接，模板 $1..$n / $name 引用提取的捕获组；
     * 提取不到回退原链接（规则仍算命中，与 LinkGo「提不到就用原文」一致）。
     * 模板结果 / 提取串不带 scheme 时逐级回退，避免把半截串当链接送出。
     */
    private fun applyExtract(rule: DispatchRule, url: String): String {
        val ep = compile(rule.extractPattern) ?: return url
        val em = ep.matcher(url)
        if (!em.find()) {
            Diag.log("Dispatch extract miss: rule='${rule.name}' url=${url.take(80)}")
            return url
        }
        val working = runCatching { em.group(0) }.getOrNull() ?: return url
        Diag.log("Dispatch extract hit: rule='${rule.name}' extracted=${working.take(80)}")
        if (rule.rewrite.isBlank()) {
            return if (schemeRef.containsMatchIn(working)) working else url
        }
        val template = namedRef.replace(rule.rewrite) { "\${" + it.groupValues[1] + "}" }
        val out = runCatching { em.replaceAll(template) }.getOrNull()
        return when {
            out != null && schemeRef.containsMatchIn(out) -> out
            schemeRef.containsMatchIn(working) -> working
            else -> url
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

    /**
     * 本次判定是否需要先做短链预解析（fork.20）：按优先级走查——在「不靠解析就能命中」
     * 的规则之前先遇到开启预解析的规则就返回 true（那条规则及它之后的规则，
     * 都要等展开结果出来才能定命中与否）。
     */
    fun needsResolve(url: String, scope: DispatchScope): Boolean {
        if (!enabled || !inited) return false
        for (rule in rules) {
            if (!rule.enabled || scope !in rule.scopes) continue
            if (rule.resolveShortLink) return true
            if (applyMatch(rule, url) != null) return false
        }
        return false
    }

    /**
     * 展开后判定（fork.20）：规则顺序不变，每条先测展开地址、再测原地址——
     * DIRECT 型规则吃展开地址；RE_DISPATCH 语义下后续规则也能对展开地址重走一遍；
     * 写给短链形态本身的规则仍有机会测中原地址，优先级不被展开打乱。
     */
    fun matchAfterResolve(url: String, resolved: String, scope: DispatchScope): Pair<DispatchRule, String>? {
        if (!enabled || !inited) return null
        for (rule in rules) {
            if (!rule.enabled || scope !in rule.scopes) continue
            val outResolved = applyMatch(rule, resolved)
            if (outResolved != null) return rule to outResolved
            if (resolved != url) {
                val outOriginal = applyMatch(rule, url)
                if (outOriginal != null) return rule to outOriginal
            }
        }
        return null
    }

    /**
     * 带短链预解析的异步判定（fork.20）：不需要解析时同步回调既有 [match] 结果；
     * 需要时先展开（失败 / 无跳转自动回退原地址判定）。异步路径的回调恒在主线程。
     */
    fun matchAsync(url: String, scope: DispatchScope, callback: (Pair<DispatchRule, String>?) -> Unit) {
        if (!needsResolve(url, scope)) {
            callback(match(url, scope))
            return
        }
        Diag.log("Dispatch matchAsync: resolving short link scope=$scope url=${url.take(80)}")
        ShortLinkResolver.resolve(url) { resolved ->
            val hit = if (resolved.isNullOrBlank() || resolved == url) {
                match(url, scope)
            } else {
                matchAfterResolve(url, resolved, scope)
            }
            Diag.log("Dispatch matchAsync: resolved=${resolved?.take(80)} hit=${hit?.first?.name}")
            callback(hit)
        }
    }

    /** 长按菜单「发送到…」强行套用：不看命中与否，模板 / 提取能套则套、套不上用原链接 */
    fun applyForced(rule: DispatchRule, url: String): String = applyMatch(rule, url) ?: url

    /** 把 [url] 按规则送到目标应用；成功返回 true，失败（未装/无组件）返回 false 由调用方回退 */
    fun dispatch(context: Context, rule: DispatchRule, url: String): Boolean {
        val intent = if (rule.action == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
        } else {
            // fork.32：补 BROWSABLE，与浏览器对外链意图的构造一致——
            // 缺它时部分目标应用（如起点 QDReader）会解析到自带浏览器入口而非原生详情页
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
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
        }.onFailure { e ->
            // fork.32：送出失败不再静默吞掉——记规则名 / 最终地址 / 异常，供诊断日志定位
            Diag.log(
                "LinkDispatch: dispatch failed rule='${rule.name}' " +
                    "url=${url.take(120)} error=${e.javaClass.simpleName}: ${e.message}"
            )
        }.getOrDefault(false)
    }

    fun enabledRuleCount(): Int = rules.count { it.enabled }

    // ---------- 规则整理（排序 / 未安装检测）：只重排与批量改启停，不动判定语义 ----------

    /**
     * 匹配精度评分（越大越具体、越靠前），「整理规则」的兜底排序键。启发式：
     * - 匹配方式基线：正则整行 300 > 域名 200 > 包含子串 100（正则能锚定整行，最具体；包含最泛）；
     * - 正则带 ^ / $ 锚点 +40（去掉开头的 (?i) 等标志组后判断首尾）；
     * - 模式串长度每字符 +1（上限 +100）：越长通常越具体；
     * - 正则里每个 .* / .+ 通配 -15：通配越多越泛。
     */
    fun specificityScore(rule: DispatchRule): Int {
        var score = when (rule.matchType) {
            0 -> 300
            1 -> 200
            else -> 100
        }
        val p = rule.pattern.trim()
        score += minOf(p.length, 100)
        if (rule.matchType == 0) {
            val body = p.replace(Regex("^\\(\\?[a-zA-Z]+\\)"), "")
            if (body.startsWith("^") || body.endsWith("$")) score += 40
            score -= 15 * Regex("\\.\\*|\\.\\+").findAll(p).count()
        }
        return score
    }

    private fun compareForOrganize(
        x: IndexedValue<DispatchRule>,
        y: IndexedValue<DispatchRule>,
        enabledFirst: Boolean,
        byName: Boolean,
        collator: Collator,
    ): Int {
        val a = x.value
        val b = y.value
        if (enabledFirst) {
            val c = b.enabled.compareTo(a.enabled)
            if (c != 0) return c
        }
        if (byName) {
            val c = collator.compare(a.name, b.name)
            if (c != 0) return c
        }
        val c = specificityScore(b).compareTo(specificityScore(a))
        if (c != 0) return c
        return x.index.compareTo(y.index)
    }

    /**
     * 按所选维度重排规则并落盘（列表顺序即分发优先级）。组合语义是确定的：
     * 1) 归类（groupByApp）为最外层：相同目标包名的规则聚成一组（目标为空 = 系统选择，自成一组）；
     *    组与组的先后由「组内排得最靠前的那条规则在全体中的名次」决定；
     * 2) 组内（未开归类时即全体）依次比较：启用优先（enabledFirst）→ 名称序（byName，
     *    Collator Locale.CHINA，中文按拼音）→ 匹配精度高者优先 → 原顺序兜底，全程稳定。
     */
    @Synchronized
    fun organize(groupByApp: Boolean, enabledFirst: Boolean, byName: Boolean) {
        if (rules.size < 2) return
        val collator = Collator.getInstance(Locale.CHINA)
        val innerSorted = rules.withIndex()
            .sortedWith(Comparator { x, y -> compareForOrganize(x, y, enabledFirst, byName, collator) })
        val result: List<DispatchRule> = if (groupByApp) {
            val groupRank = HashMap<String, Int>()
            innerSorted.forEachIndexed { pos, iv ->
                groupRank.putIfAbsent(iv.value.targetPackage.trim(), pos)
            }
            innerSorted.mapIndexed { pos, iv ->
                Triple(groupRank.getValue(iv.value.targetPackage.trim()), pos, iv.value)
            }.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
        } else {
            innerSorted.map { it.value }
        }
        rules.clear()
        rules.addAll(result)
        saveLocked()
    }

    /**
     * 目标应用未安装的规则（目标包名为空 = 系统选择，豁免检测）。
     * 纯本地 PackageManager 查询；任意包名可查依赖 Manifest 的 QUERY_ALL_PACKAGES 声明
     * （Android 11+ 包可见性），查询结果只用于整理面板计数，不出设备。
     */
    fun uninstalledRules(context: Context): List<DispatchRule> {
        val pm = context.packageManager
        return rules.filter { rule ->
            val pkg = rule.targetPackage.trim()
            pkg.isNotEmpty() && !isPackageInstalled(pm, pkg)
        }
    }

    private fun isPackageInstalled(pm: PackageManager, pkg: String): Boolean =
        runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess

    /** 把给定 id 的规则全部停用并落盘（整理面板「一键停用」用） */
    @Synchronized
    fun disableByIds(ids: Set<String>) {
        var changed = false
        for (i in rules.indices) {
            val r = rules[i]
            if (r.id in ids && r.enabled) {
                rules[i] = r.copy(enabled = false)
                changed = true
            }
        }
        if (changed) saveLocked()
    }

    /** 把给定 id 的规则整体移到列表末尾（保持相对顺序）并落盘（整理面板「移到末尾」用） */
    @Synchronized
    fun moveToEndByIds(ids: Set<String>) {
        if (ids.isEmpty()) return
        val stay = rules.filter { it.id !in ids }
        val tail = rules.filter { it.id in ids }
        if (tail.isEmpty() || stay.size == rules.size) return
        rules.clear()
        rules.addAll(stay)
        rules.addAll(tail)
        saveLocked()
    }
}
