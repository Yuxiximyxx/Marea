package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import io.legado.app.ui.login.SourceLoginType
import io.legado.app.ui.replace.ReplaceEditRoute
import kotlinx.serialization.Serializable

@Serializable
sealed interface MainRoute : NavKey

@Serializable
data class MainRouteToc(val bookUrl: String, val initialPage: Int = 0) : MainRoute

@Serializable
data class MainRouteBookInfoEdit(val bookUrl: String) : MainRoute

@Serializable
data class MainRouteReplaceRules(val bookUrl: String? = null) : MainRoute

@Serializable
data class MainRouteReplaceEdit(val editor: ReplaceEditRoute) : MainRoute

@Serializable
data object MainRouteHome : MainRoute

@Serializable
data object MainRouteSettings : MainRoute

@Serializable
data object MainRouteSettingsOther : MainRoute

@Serializable
data object MainRouteSettingsRead : MainRoute

@Serializable
data object MainRouteSettingsCover : MainRoute

@Serializable
data object MainRouteSettingsCoverAlbums : MainRoute

@Serializable
data object MainRouteSettingsTheme : MainRoute

@Serializable
data object MainRouteSettingsBackup : MainRoute

@Serializable
data object MainRouteSettingsCustomTheme : MainRoute

@Serializable
data object MainRouteSettingsThemeManage : MainRoute

@Serializable
data object MainRouteSettingsPrivate : MainRoute

@Serializable
data object MainRouteSettingsLabConfig : MainRoute

@Serializable
data object MainRouteSettingsDownloadCache : MainRoute

@Serializable
data object MainRouteSettingsTranslation : MainRoute

@Serializable
data object MainRouteImportLocal : MainRoute

@Serializable
data object MainRouteImportRemote : MainRoute

@Serializable
data object MainRouteReadRecord : MainRoute

@Serializable
data object MainRouteReadRecordOverview : MainRoute

@Serializable
data class MainRouteCache(val groupId: Long) : MainRoute

@Serializable
data object MainRouteBookCacheManage : MainRoute

@Serializable
data class MainRouteReadBook(
    val bookUrl: String? = null,
    val readAloud: Boolean = false,
    val inBookshelf: Boolean = true,
    val chapterChanged: Boolean = false,
    val sharedCoverKey: String? = null,
) : MainRoute

@Serializable
data class MainRouteReadManga(
    val bookUrl: String? = null,
    val inBookshelf: Boolean = true,
    val chapterChanged: Boolean = false,
    /** Distinguishes repeated open requests for the same book after an external TOC selection. */
    val openRequestId: Long = 0L,
    val sharedCoverKey: String? = null,
) : MainRoute

@Serializable
data class MainRouteBookInfo(
    val name: String?,
    val author: String?,
    val bookUrl: String,
    val origin: String? = null,
    val coverPath: String? = null,
    val sharedCoverKey: String? = null,
    val openRequestId: Long = 0L,
) : MainRoute

@Serializable
data class MainRouteSearchContent(
    val bookUrl: String,
    val searchWord: String? = null,
    val searchResultIndex: Int = 0,
    val autoFocus: Boolean = true,
) : MainRoute

@Serializable
data object MainRouteHighlightTagRule : MainRoute

@Serializable
data object MainRouteDictRule : MainRoute

/**
 * TXT 目录规则列表。
 *
 * [pickForBookUrl] 非空表示**选择模式**：这是为 [MainRouteToc] 挑一条规则而打开的，
 * 选中后把结果回传给那个目录页（见 `BookPageResult.TxtTocRuleChosen`），而不是切换选中态。
 */
@Serializable
data class MainRouteTxtTocRule(
    val initialRule: String? = null,
    val pickForBookUrl: String? = null,
) : MainRoute

/** TXT 目录规则预览：把一条规则套到 [bookUrl] 这本书上，可应用，也可跳到规则列表。 */
@Serializable
data class MainRouteTxtTocRulePreview(
    val bookUrl: String,
    val currentTocRegex: String? = null,
) : MainRoute

@Serializable
data object MainRouteAllBookmark : MainRoute

@Serializable
data object MainRouteAbout : MainRoute


object MainRouteConst {
    const val ROUTE_MAIN = "main"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_SETTINGS_OTHER = "settings/other"
    const val ROUTE_SETTINGS_READ = "settings/read"
    const val ROUTE_SETTINGS_COVER = "settings/cover"
    const val ROUTE_SETTINGS_COVER_ALBUMS = "settings/cover/albums"
    const val ROUTE_SETTINGS_THEME = "settings/theme"
    const val ROUTE_SETTINGS_BACKUP = "settings/backup"
    const val ROUTE_SETTINGS_CUSTOM_THEME = "settings/custom_theme"
    const val ROUTE_SETTINGS_PRIVATE = "settings/private"
    const val ROUTE_SETTINGS_LAB_CONFIG = "settings/lab_config"
    const val ROUTE_SETTINGS_DOWNLOAD_CACHE = "settings/download_cache"
    const val ROUTE_SETTINGS_TRANSLATION = "settings/translation"
    const val ROUTE_IMPORT_LOCAL = "import/local"
    const val ROUTE_IMPORT_REMOTE = "import/remote"
    const val ROUTE_CACHE = "cache"
    const val ROUTE_BOOK_CACHE_MANAGE = "book/cache/manage"
    const val ROUTE_READ_BOOK = "book/read"
    const val ROUTE_READ_MANGA = "book/read/manga"
    const val ROUTE_SEARCH_CONTENT = "book/searchContent"
    const val ROUTE_BOOK_INFO = "book/info"
    const val ROUTE_READ_RECORD = "read_record"
    const val ROUTE_READ_RECORD_OVERVIEW = "read_record_overview"
    const val ROUTE_ABOUT = "about"
}
