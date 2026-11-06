package dev.dsh.miuixbrowser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 用户脚本的原始能力桥：GM_setValue / GM_xmlhttpRequest / GM_notification / GM_openInTab 等。
 * 跨域请求由原生发起，绕开网页的 CORS 限制（与油猴一致）。
 *
 * 注意：addJavascriptInterface 会注入到所有框架，因此 xhr 采用「发起 + 轮询」两步，
 * 结果由发起请求的那个框架自己取走，而不是用 evaluateJavascript 往主框架推。
 */
class GmBridge(
    private val context: Context,
    @Suppress("unused") private val webViewProvider: () -> WebView?,
    private val onOpenTab: (String) -> Unit,
    private val onDownload: (url: String, name: String) -> Unit = { _, _ -> },
) {

    private val prefs = context.getSharedPreferences("userscript_gm", Context.MODE_PRIVATE)
    private val pool = Executors.newCachedThreadPool()
    private val mainHandler = android.os.Handler(context.mainLooper)

    private val results = ConcurrentHashMap<String, String>()
    private val connections = ConcurrentHashMap<String, HttpURLConnection>()
    private val aborted = ConcurrentHashMap.newKeySet<String>()

    // ------------------------------------------------------------------ values

    @JavascriptInterface
    fun getValue(scriptId: String, key: String): String? = prefs.getString("$scriptId::$key", null)

    @JavascriptInterface
    fun setValue(scriptId: String, key: String, value: String) {
        prefs.edit().putString("$scriptId::$key", value).apply()
    }

    @JavascriptInterface
    fun deleteValue(scriptId: String, key: String) {
        prefs.edit().remove("$scriptId::$key").apply()
    }

    @JavascriptInterface
    fun listValues(scriptId: String): String {
        val prefix = "$scriptId::"
        val keys = prefs.all.keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
        return JSONArray(keys).toString()
    }

    // ------------------------------------------------------------------ GM_xmlhttpRequest

    @JavascriptInterface
    fun xhrStart(reqId: String, optionsJson: String, bodyBase64: String) {
        pool.execute {
            val result = runCatching { runXhr(reqId, optionsJson, bodyBase64) }.getOrElse { e ->
                JSONObject().put("state", "error").put("error", e.message ?: "request failed").toString()
            }
            connections.remove(reqId)
            if (aborted.remove(reqId)) {
                results[reqId] = JSONObject().put("state", "abort").toString()
            } else {
                results[reqId] = result
            }
        }
    }

    /** 还没好返回 null；好了返回 JSON 并清掉 */
    @JavascriptInterface
    fun xhrPoll(reqId: String): String? = results.remove(reqId)

    @JavascriptInterface
    fun xhrAbort(reqId: String) {
        aborted.add(reqId)
        connections[reqId]?.let { conn -> pool.execute { runCatching { conn.disconnect() } } }
    }

    private fun hasHeader(headers: JSONObject, name: String): Boolean =
        headers.keys().asSequence().any { it.equals(name, ignoreCase = true) }

    private fun runXhr(reqId: String, optionsJson: String, bodyBase64: String): String {
        val opt = JSONObject(optionsJson)
        var method = opt.optString("method", "GET").ifBlank { "GET" }.uppercase()
        val headers = opt.optJSONObject("headers") ?: JSONObject()
        val timeoutMs = opt.optInt("timeout", 0).takeIf { it > 0 } ?: 30_000
        val anonymous = opt.optBoolean("anonymous", false)
        var body: ByteArray? = if (bodyBase64.isNotEmpty()) android.util.Base64.decode(bodyBase64, android.util.Base64.DEFAULT) else null
        var currentUrl = opt.getString("url")
        val cookieManager = CookieManager.getInstance()

        try {
            var redirects = 0
            while (true) {
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    instanceFollowRedirects = false // 手动跟随，才能处理 http→https 与 Cookie
                    useCaches = false
                }
                connections[reqId] = conn
                if (aborted.contains(reqId)) {
                    conn.disconnect()
                    return JSONObject().put("state", "abort").toString()
                }
                // 让系统自己处理压缩，不接受用户指定的 Accept-Encoding
                headers.keys().forEach { name ->
                    if (!name.equals("Accept-Encoding", true)) conn.setRequestProperty(name, headers.optString(name))
                }
                if (!hasHeader(headers, "User-Agent")) {
                    conn.setRequestProperty("User-Agent", opt.optString("ua", "Mozilla/5.0 Mafari"))
                }
                if (!hasHeader(headers, "Accept")) conn.setRequestProperty("Accept", "*/*")
                if (!hasHeader(headers, "Accept-Language")) {
                    conn.setRequestProperty("Accept-Language", opt.optString("lang", "en-US").ifBlank { "en-US" })
                }
                if (!anonymous && !hasHeader(headers, "Cookie")) {
                    cookieManager.getCookie(currentUrl)?.takeIf { it.isNotBlank() }?.let {
                        conn.setRequestProperty("Cookie", it)
                    }
                }
                val payload = body
                if (payload != null && payload.isNotEmpty() && method != "GET" && method != "HEAD") {
                    conn.doOutput = true
                    conn.setFixedLengthStreamingMode(payload.size)
                    conn.outputStream.use { it.write(payload) }
                }

                val status = conn.responseCode
                if (!anonymous) {
                    conn.headerFields?.forEach { (name, values) ->
                        if (name != null && name.equals("Set-Cookie", true)) {
                            values.forEach { cookieManager.setCookie(currentUrl, it) }
                        }
                    }
                }
                if (status in intArrayOf(301, 302, 303, 307, 308) && redirects < 10 &&
                    opt.optString("redirect", "follow") != "manual"
                ) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        redirects++
                        currentUrl = URL(URL(currentUrl), location).toString()
                        if (status == 303 || ((status == 301 || status == 302) && method == "POST")) {
                            method = "GET"
                            body = null
                        }
                        conn.disconnect()
                        continue
                    }
                }

                val stream = if (status in 200..399) conn.inputStream else conn.errorStream
                val bytes = ByteArrayOutputStream()
                stream?.use { input ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        bytes.write(buf, 0, n)
                    }
                }
                val rawHeaders = StringBuilder()
                conn.headerFields?.forEach { (name, values) ->
                    if (name != null) values.forEach { rawHeaders.append(name).append(": ").append(it).append("\r\n") }
                }
                val out = JSONObject()
                    .put("state", "load")
                    .put("status", status)
                    .put("statusText", conn.responseMessage ?: "")
                    .put("finalUrl", currentUrl)
                    .put("headers", rawHeaders.toString())
                    .put("body", android.util.Base64.encodeToString(bytes.toByteArray(), android.util.Base64.NO_WRAP))
                    .toString()
                conn.disconnect()
                return out
            }
        } catch (e: SocketTimeoutException) {
            return JSONObject().put("state", "timeout").put("error", "timeout").toString()
        }
    }

    // ------------------------------------------------------------------ 流式写文件（GM.openFileStream）
    // WebView 的 showSaveFilePicker 在部分环境不弹选择器；脚本可改用原生流式写入 Download/mafari，
    // 逐段写盘、不占内存，适合 M3U8 等大文件。只允许写入 Download/mafari，文件名会被净化。

    private class FileStream(val uri: android.net.Uri, val out: java.io.OutputStream)

    private val streams = ConcurrentHashMap<String, FileStream>()

    @JavascriptInterface
    fun streamOpen(name: String, mime: String): String = runCatching {
        val safe = name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim().trim('.').take(120)
            .ifBlank { "download_${System.currentTimeMillis()}" }
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, safe)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, mime.ifBlank { "application/octet-stream" })
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/mafari")
            put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("insert failed")
        val out = resolver.openOutputStream(uri, "w") ?: run { resolver.delete(uri, null, null); error("open failed") }
        val id = java.util.UUID.randomUUID().toString()
        streams[id] = FileStream(uri, out.buffered(256 * 1024))
        val actual = resolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: safe
        JSONObject().put("id", id).put("name", actual).toString()
    }.getOrElse { JSONObject().put("error", it.message ?: "error").toString() }

    /** 返回空串表示成功，否则为错误信息 */
    @JavascriptInterface
    fun streamWrite(id: String, base64: String): String = runCatching {
        val s = streams[id] ?: error("stream closed")
        s.out.write(android.util.Base64.decode(base64, android.util.Base64.DEFAULT))
        ""
    }.getOrElse { it.message ?: "write failed" }

    @JavascriptInterface
    fun streamClose(id: String): String = runCatching {
        val s = streams.remove(id) ?: error("stream closed")
        s.out.flush(); s.out.close()
        context.contentResolver.update(
            s.uri,
            android.content.ContentValues().apply { put(android.provider.MediaStore.Downloads.IS_PENDING, 0) },
            null, null,
        )
        ""
    }.getOrElse { it.message ?: "close failed" }

    @JavascriptInterface
    fun streamAbort(id: String) {
        val s = streams.remove(id) ?: return
        runCatching { s.out.close() }
        runCatching { context.contentResolver.delete(s.uri, null, null) }
    }

    // ------------------------------------------------------------------ misc

    @JavascriptInterface
    fun toast(message: String) {
        mainHandler.post {
            runCatching {
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JavascriptInterface
    fun openTab(url: String) {
        mainHandler.post { onOpenTab(url) }
    }

    @JavascriptInterface
    fun download(url: String, name: String) {
        mainHandler.post { onDownload(url, name) }
    }

    @JavascriptInterface
    fun copy(text: String) {
        mainHandler.post {
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Mafari", text))
            }
        }
    }

    @JavascriptInterface
    fun cookies(url: String): String =
        runCatching { CookieManager.getInstance().getCookie(url).orEmpty() }.getOrDefault("")

    @JavascriptInterface
    fun setCookie(url: String, cookie: String) {
        runCatching { CookieManager.getInstance().setCookie(url, cookie) }
    }
}
