package dev.dsh.miuixbrowser

import android.content.Context
import android.util.Base64
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 隐私模式（fork.5）的密码保险库：只存「盐 + SHA-256(盐‖密码)」的 Base64，
 * 明文密码从不落盘。校验在地址栏输入时发生（见 BrowserTab.load 的拦截）。
 */
object PrivacyVault {

    private const val PREFS = "marea_privacy_vault"
    private const val KEY_SALT = "salt"
    private const val KEY_HASH = "hash"

    // fork.6 防御：保险库的全部 prefs 读写都包 runCatching——设置页在「无口令」初始状态下
    // 也会走 isSet 读取，任何存储层异常都不许冒泡成闪退，最差按「未设置 / 校验不通过」处理。
    private fun prefs(context: Context) =
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }.getOrNull()

    fun isSet(context: Context): Boolean =
        prefs(context)?.contains(KEY_HASH) ?: false

    fun setPassword(context: Context, password: String) {
        runCatching {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            prefs(context)?.edit()
                ?.putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                ?.putString(KEY_HASH, Base64.encodeToString(hash(salt, password), Base64.NO_WRAP))
                ?.apply()
        }
    }

    fun clear(context: Context) {
        runCatching { prefs(context)?.edit()?.clear()?.apply() }
    }

    fun verify(context: Context, candidate: String): Boolean = runCatching {
        val prefs = prefs(context) ?: return@runCatching false
        val saltB64 = prefs.getString(KEY_SALT, null) ?: return@runCatching false
        val hashB64 = prefs.getString(KEY_HASH, null) ?: return@runCatching false
        val salt = runCatching { Base64.decode(saltB64, Base64.NO_WRAP) }.getOrNull()
            ?: return@runCatching false
        val expected = runCatching { Base64.decode(hashB64, Base64.NO_WRAP) }.getOrNull()
            ?: return@runCatching false
        MessageDigest.isEqual(expected, hash(salt, candidate))
    }.getOrDefault(false)

    private fun hash(salt: ByteArray, password: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(password.toByteArray(Charsets.UTF_8))
    }
}

/**
 * 隐私模式的全局状态（进程级 Compose 状态）：BrowserScreen 观察它做空间切换，
 * 各列表页（书签 / 历史 / 下载）观察它决定显示哪一套数据。
 * 切后台自动退出由 MainActivity.onStop 调用 [exit] 触发。
 */
object PrivacyMode {

    const val PROFILE_NAME = "marea_private"

    var active by mutableStateOf(false)
        private set

    fun enter() {
        active = true
    }

    fun exit() {
        active = false
    }

    @Volatile
    private var profileSupportedCache: Boolean? = null

    /**
     * fork.9：只读缓存、绝不触发检测本身。隐私设置页改用这个值显示——页面里不再出现
     * 任何 WebView/Profile 原生 API 的首次触碰；缓存由 MainActivity 启动时在主线程
     * 预热（见 [profileSupported]），或由真正切换隐私空间的 applyPrivacySpace 顺带填充。
     * 缓存未填充时按「不支持」显示（仅文案降级，真正的隔离判定在切换空间时仍会现算）。
     */
    fun profileSupportedCached(): Boolean = profileSupportedCache ?: false

    /**
     * 设备 WebView 是否支持多 Profile（Cookie / 存储隔离的前提）。
     * fork.6：结果按进程缓存，只在首次调用时碰 WebViewFeature——设置页组合阶段不再反复裸调
     * 厂商 WebView 的特性检测，检测本身异常也只按「不支持」降级，绝不冒泡。
     */
    fun profileSupported(): Boolean {
        profileSupportedCache?.let { return it }
        val result = runCatching {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        }.getOrDefault(false)
        profileSupportedCache = result
        return result
    }

    /**
     * 按空间取 Cookie：隐私空间且 Profile 可用时读隐私 Profile 的 CookieManager，
     * 否则（普通空间 / Profile 不可用）读默认 CookieManager——后者即隔离边界的回退。
     */
    fun cookieFor(url: String, privacy: Boolean): String? {
        if (privacy && profileSupported()) {
            val profileCookie = runCatching {
                ProfileStore.getInstance().getOrCreateProfile(PROFILE_NAME).cookieManager.getCookie(url)
            }.getOrNull()
            if (profileCookie != null) return profileCookie
        }
        return runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
    }
}
