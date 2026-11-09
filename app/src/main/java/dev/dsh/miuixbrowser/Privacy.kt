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

    fun isSet(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_HASH)

    fun setPassword(context: Context, password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash(salt, password), Base64.NO_WRAP))
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun verify(context: Context, candidate: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saltB64 = prefs.getString(KEY_SALT, null) ?: return false
        val hashB64 = prefs.getString(KEY_HASH, null) ?: return false
        val salt = runCatching { Base64.decode(saltB64, Base64.NO_WRAP) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(hashB64, Base64.NO_WRAP) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, hash(salt, candidate))
    }

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

    /** 设备 WebView 是否支持多 Profile（Cookie / 存储隔离的前提） */
    fun profileSupported(): Boolean =
        runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)

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
