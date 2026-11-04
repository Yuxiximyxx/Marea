package dev.dsh.miuixbrowser

/** 内置脚本：不进 prefs，按需一键加入脚本列表。去广告规则不在这里，见「设置 → 高级 → 去广告」。 */
data class BuiltinScript(
    val name: String,
    val description: String,
    /** 直接是 .user.js 的订阅地址 */
    val userScriptUrl: String = "",
    /** 没有地址时直接加入的脚本内容 */
    val code: String = "",
)

val BUILTIN_SCRIPTS = listOf(
    BuiltinScript(
        name = "Adblock4limbo",
        description = "limbopro/Adblock4limbo 的通用去广告脚本，添加后随页面加载注入",
        userScriptUrl = "https://github.com/limbopro/Adblock4limbo/raw/refs/heads/main/Adguard/Adblock4limbo.user.js",
    ),
)
