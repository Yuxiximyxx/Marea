package dev.dsh.miuixbrowser

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import java.util.concurrent.Executor

/**
 * WebView 代理：使用官方 [ProxyController]（androidx.webkit 公共 API）。
 * 对进程内全部 WebView 生效；关闭时 clearProxyOverride。
 * 规则格式见 ProxyConfig：`[scheme://]host[:port]`，SOCKS 用 socks5://。
 */
object WebViewProxy {

    private val mainExecutor = Executor { it.run() }

    /** type：0 = HTTP，1 = SOCKS5 */
    fun apply(enabled: Boolean, type: Int, host: String, port: Int) {
        val controller = runCatching { ProxyController.getInstance() }.getOrNull() ?: return
        val h = host.trim()
        val p = port
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
        )
    }
}
