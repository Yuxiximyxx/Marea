package dev.dsh.miuixbrowser

/** 常见第三方统计 / 追踪域名（只拦第三方子资源请求，命中即返回空响应）。 */
object TrackerList {
    private val domains = setOf(
        "google-analytics.com", "analytics.google.com", "googletagmanager.com", "googletagservices.com",
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "connect.facebook.net", "facebook.com/tr", "graph.facebook.com", "pixel.facebook.com",
        "hotjar.com", "mixpanel.com", "segment.io", "segment.com", "amplitude.com", "fullstory.com",
        "clarity.ms", "scorecardresearch.com", "quantserve.com", "taboola.com", "outbrain.com",
        "criteo.com", "adnxs.com", "rubiconproject.com", "pubmatic.com", "openx.net", "moatads.com",
        "bugsnag.com", "sentry.io", "newrelic.com", "nr-data.net", "branch.io", "adjust.com", "appsflyer.com",
        "hm.baidu.com", "cnzz.com", "umeng.com", "51.la", "growingio.com", "sensorsdata.cn",
        "mmstat.com", "tanx.com", "cm.g.doubleclick.net", "bat.bing.com", "ads.twitter.com",
        "analytics.twitter.com", "ads.linkedin.com", "px.ads.linkedin.com", "snap.licdn.com",
        "analytics.tiktok.com", "ads-api.tiktok.com", "log.byteoversea.com", "mon.zijieapi.com",
    ).map { it.substringBefore('/') }.toSet()

    fun matches(host: String): Boolean {
        val h = host.lowercase()
        return domains.any { h == it || h.endsWith(".$it") }
    }
}
