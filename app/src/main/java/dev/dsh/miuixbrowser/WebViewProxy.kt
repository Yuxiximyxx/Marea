package dev.dsh.miuixbrowser

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import java.util.concurrent.Executor

/**
 * WebView 代理：官方 [ProxyController]（androidx.webkit）。
 * 对进程内全部 WebView 生效；关闭时 clearProxyOverride。
 * HTTP 账号密码不进 ProxyConfig（API 不支持），由 WebViewClient
 * [onReceivedHttpAuthRequest] 在挑战主机等于代理主机时提交。
 */
object WebViewProxy {

    private val mainExecutor = Executor { it.run() }

    @Volatile var host: String = ""
        private set
    @Volatile var authEnabled: Boolean = false
        private set
    @Volatile var authUser: String = ""
        private set
    @Volatile var authPass: String = ""
        private set

    /** 是否应对该主机的 HTTP 认证挑战自动提交代理账号 */
    fun shouldAutoAuth(challengeHost: String): Boolean =
        authEnabled && host.isNotEmpty() && challengeHost.equals(host, ignoreCase = true)

    /** type：0 = HTTP，1 = SOCKS5 */
    fun apply(
        enabled: Boolean,
        type: Int,
        host: String,
        port: Int,
        auth: Boolean = false,
        user: String = "",
        pass: String = "",
    ) {
        val controller = runCatching { ProxyController.getInstance() }.getOrNull() ?: return
        val h = host.trim()
        val p = port
        this.host = if (enabled) h else ""
        this.authEnabled = enabled && auth && h.isNotEmpty()
        this.authUser = user
        this.authPass = pass
        if (!enabled || h.isEmpty() || p !in 1..65535) {
            runCatching { controller.clearProxyOverride(mainExecutor, {}) }
            return
        }
        val rule = if (type == 1) {
            "socks5://$h:$p"
        } else {
            "http://$h:$p"
        }
        val config = runCatching {
            ProxyConfig.Builder()
                .addProxyRule(rule)
                .build()
        }.getOrNull() ?: return
        runCatching {
            controller.setProxyOverride(config, mainExecutor, {})
        }
    }

    fun applyFrom(settings: AppSettings) {
        apply(
            enabled = settings.proxyEnabled,
            type = settings.proxyType,
            host = settings.proxyHost,
            port = settings.proxyPort,
            auth = settings.proxyAuth,
            user = settings.proxyUser,
            pass = settings.proxyPass,
        )
    }
}
