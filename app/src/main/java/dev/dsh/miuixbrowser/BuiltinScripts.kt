package dev.dsh.miuixbrowser

/** 建议安装的脚本：不进 prefs，按需一键加入脚本列表。去广告规则不在这里，见「设置 → 高级 → 去广告」。 */
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
    BuiltinScript(
        name = "M3U8媒体下载器",
        description = "智能嗅探 m3u8、多线程下载，支持 AES-128 解密",
        userScriptUrl = "https://update.greasyfork.org/scripts/584352/M3U8%E5%AA%92%E4%BD%93%E4%B8%8B%E8%BD%BD%E5%99%A8%20%28%E6%94%AF%E6%8C%81AES-128%E8%A7%A3%E5%AF%86%29.user.js",
    ),
    BuiltinScript(
        name = "万能网页限制解除",
        description = "解除复制、右键、文本选择、粘贴、拖拽、键盘等网页限制",
        userScriptUrl = "https://update.greasyfork.org/scripts/586251/%E4%B8%87%E8%83%BD%E7%BD%91%E9%A1%B5%E9%99%90%E5%88%B6%E8%A7%A3%E9%99%A4.user.js",
    ),
    BuiltinScript(
        name = "深色模式切换器",
        description = "基于 DarkReader，一键切换任意网页深色/夜间模式",
        userScriptUrl = "https://update.greasyfork.org/scripts/583374/%E6%B7%B1%E8%89%B2%E6%A8%A1%E5%BC%8F%E5%88%87%E6%8D%A2%E5%99%A8%20%28Dark%20Mode%20Switcher%29.user.js",
    ),
)
