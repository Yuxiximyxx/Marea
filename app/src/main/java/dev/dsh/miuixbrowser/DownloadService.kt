package dev.dsh.miuixbrowser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import org.json.JSONObject

/** 下载前台服务：常驻进度通知（HyperOS 会把它显示在通知栏/超级岛区域） */
class DownloadService : Service() {

    private var islandShown = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = build("准备下载", 0, true, "0%")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "下载",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "文件下载进度"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** OS3（超岛协议）才注入岛参数；更低版本走普通焦点通知 */
    private fun islandProtocol(): Int = runCatching {
        Settings.System.getInt(contentResolver, "notification_focus_protocol", 0)
    }.getOrDefault(0)

    private fun islandJson(name: String, percent: Int, text: String): String {
        val param = JSONObject()
            .put("protocol", 1)
            .put("business", "download")
            .put("enableFloat", true)
            .put("updatable", true)
            .put("ticker", "下载中 $percent%")
            .put("aodTitle", "下载中 $percent%")

        val bigArea = JSONObject().apply {
            put(
                "imageTextInfoLeft",
                JSONObject().apply {
                    put("type", 1)
                    put(
                        "textInfo",
                        JSONObject().apply {
                            put("frontTitle", "下载中")
                            put("title", "$percent%")
                            put("content", text)
                            put("useHighLight", false)
                        },
                    )
                },
            )
            put(
                "textInfo",
                JSONObject().apply {
                    put("frontTitle", name)
                    put("title", "$percent%")
                    put("content", text)
                },
            )
        }
        val smallArea = JSONObject().apply {
            put(
                "textInfo",
                JSONObject().apply {
                    put("frontTitle", "$percent%")
                    put("title", "下载中")
                    put("content", name)
                },
            )
        }
        val island = JSONObject()
            .put("islandProperty", 1)
            .put("bigIslandArea", bigArea)
            .put("smallIslandArea", smallArea)

        val baseInfo = JSONObject().apply {
            put("title", "正在下载 $name")
            put("content", text)
            put("colorTitle", "#006EFF")
            put("type", 2)
        }
        val hintInfo = JSONObject().apply {
            put("type", 1)
            put("title", "下载中")
        }
        param.put("param_island", island)
        param.put("baseInfo", baseInfo)
        param.put("hintInfo", hintInfo)

        return JSONObject().put("param_v2", param).toString()
    }

    private fun build(
        title: String,
        progress: Int,
        indeterminate: Boolean,
        text: String,
        islandName: String? = null,
    ): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
        val notification = builder.build()
        // 只在「开始下载」这一次带岛参数，进度更新不再注入，避免岛反复弹出
        if (islandName != null && !islandShown && islandProtocol() >= 3) {
            runCatching {
                notification.extras.putString(
                    "miui.focus.param",
                    islandJson(islandName, progress, text),
                )
                notification.extras.putString("miui.focus.business", "download")
                islandShown = true
            }
        }
        return notification
    }

    /** 开始下载：这里才带岛参数 */
    fun showStart(name: String) {
        islandShown = false
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, build("正在下载", 0, true, name, name))
            islandShown = true
        }
    }

    /** 进度更新：普通通知，不再上岛 */
    fun update(name: String, progress: Float, text: String) {
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, build(name, (progress * 100).toInt(), false, text, null))
        }
    }

    fun finish(text: String) {
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, build("下载完成", 100, false, text, text))
        }
        runCatching {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    companion object {
        const val CHANNEL_ID = "mafari_download"
        const val NOTIFICATION_ID = 4201
        @Volatile
        var instance: DownloadService? = null

        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, DownloadService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
