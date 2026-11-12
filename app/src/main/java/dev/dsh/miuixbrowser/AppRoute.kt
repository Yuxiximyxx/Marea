package dev.dsh.miuixbrowser

import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 应用级路由（miuix-nav 官方 NavDisplay 的返回栈元素）。
 *
 * 栈底永远是 [Browser]（浏览主界面）；设置、关于、下载、扩展、书签/历史作为页面压栈。
 * 预见性返回的跟手、提交/取消回弹、下层视差与遮罩全部由 NavDisplay 负责，应用不再自绘。
 * 栈是纯内存栈（navBackStackOf），所以这里不需要 @Serializable。
 */
sealed interface AppRoute : NavKey {
    data object Browser : AppRoute
    data object Settings : AppRoute
    data object About : AppRoute
    data object Downloads : AppRoute
    data object Extensions : AppRoute

    /** 设置 → 高级 → 去广告 */
    data object AdBlockPage : AppRoute

    /** 设置 → 高级 → 链接分发 */
    data object DispatchPage : AppRoute

    /** 设置 → 隐私与安全 → 隐私模式 */
    data object PrivacyPage : AppRoute

    // 指纹保护页已随上游改名并入 PrivacyEnhancePage（原 FingerprintPage 路由废弃，功能无损）

    /** 设置 → 隐私与安全 → Cookie 管理 */
    data object CookiePage : AppRoute

    /** 设置 → 隐私与安全 → 隐私增强保护（WebView：DNT / WebRTC / 指纹） */
    data object PrivacyEnhancePage : AppRoute

    /** 书签 / 历史库。[tab] 0 = 书签，1 = 历史。 */
    data class Library(val tab: Int) : AppRoute

    /** TXT 阅读器书架（fork.27），空间按进入时的隐私模式状态取 */
    data object Bookshelf : AppRoute

    /** TXT 阅读设置页（fork.28）：阅读器设置的统一落点，从书架页齿轮进入 */
    data object TxtReaderSettings : AppRoute

    /** TXT 目录规则页（fork.28）：阅读设置下的独立页，与阅读器内管理共用数据 */
    data object TxtTocRules : AppRoute
}
