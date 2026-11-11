package dev.dsh.miuixbrowser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 下载前台服务 + 下载通知（fork.2 起改走 Android Live Updates）：
 * - 前台服务只挂一条聚合摘要通知（NOTIFICATION_ID），保证服务存活与点击入口；
 * - 每个下载任务有自己的一条进度通知（ID 按任务分配，互不覆盖）：
 *   API 36+ 用 NotificationCompat.ProgressStyle + setRequestPromotedOngoing(true) 申请实况通知，
 *   分段下载把分段表映射成 ProgressStyle 的 segments / points；
 *   API 36 以下或不满足条件时就是普通进度通知（经典 setProgress），行为不回退。
 * - 暂停/继续/取消动作的 PendingIntent 按任务派生 requestCode，避免多任务共用一个被 extras 串扰。
 * 超级岛（miui.focus）参数保持一期做法，附在任务通知上（荣耀等非小米系统忽略即可）。
 */
class DownloadService : Service() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildSummary(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // 进程被杀后下载任务已落盘、恢复成暂停，不需要系统用空 intent 重启出一个空转的前台服务
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** 聚合摘要随任务状态变化刷新（服务已在前台时用 notify 更新同一条） */
    internal fun refreshForeground() {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildSummary(this))
        }
    }

    /** 没有活跃任务了：摘掉前台摘要并停服务（各任务自己的通知不受影响） */
    internal fun stopNow() {
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    companion object {
        const val CHANNEL_ID = "mafari_download"

        /** 前台服务的聚合摘要通知 ID；任务通知从 TASK_ID_BASE 起按任务分配 */
        const val NOTIFICATION_ID = 4201
        private const val TASK_ID_BASE = 5000

        @Volatile
        var instance: DownloadService? = null

        private val idSeq = AtomicInteger(TASK_ID_BASE)
        private val idMap = ConcurrentHashMap<Long, Int>()

        /** 任务 ID（nanoTime）→ 通知 ID 的稳定映射，同一任务全程用同一个 */
        fun notifId(taskId: Long): Int = idMap.getOrPut(taskId) { idSeq.getAndIncrement() }

        private class Entry(
            var name: String,
            /** -1 = 还不知道总量（不确定进度） */
            var percent: Int,
            var text: String,
            var paused: Boolean,
        )

        /** 活跃任务（下载中 + 已暂停）的通知侧镜像，驱动聚合摘要与服务存活判断 */
        private val entries = ConcurrentHashMap<Long, Entry>()

        @Volatile
        private var lastTaskId: Long = -1L

        /** 超级岛每个任务只允许浮出一次 */
        private val islandFloated = ConcurrentHashMap.newKeySet<Long>()

        @Volatile
        private var channelReady = false

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

        private fun manager(context: Context): NotificationManager {
            if (!channelReady) ensureChannel(context)
            return context.getSystemService(NotificationManager::class.java)
        }

        private fun ensureChannel(context: Context) {
            runCatching {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "下载",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "文件下载进度"
                    setShowBadge(false)
                }
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
                channelReady = true
            }
        }

        // ---------- 任务通知：Downloader 的唯一入口 ----------

        /** 任务开始（或继续/重试）：登记并发出不确定进度通知，后续 update 刷成确定进度 */
        fun showStart(context: Context, name: String, taskId: Long) {
            entries[taskId] = Entry(name, -1, name, paused = false)
            lastTaskId = taskId
            islandFloated.remove(taskId)
            postTask(context, taskId, title = "正在下载", percent = -1, text = name, paused = false, allowFloat = true)
            instance?.refreshForeground()
        }

        /**
         * 进度更新。[segPoints] 是 ProgressStyle 的分段划分（各段点数，合计 100），
         * [segMarks] 是已下完分段的边界点位；只在 API 36+ 的 ProgressStyle 上用，低版本忽略。
         */
        fun update(
            context: Context,
            name: String,
            progress: Float,
            text: String,
            taskId: Long,
            segPoints: List<Int>? = null,
            segMarks: List<Int>? = null,
        ) {
            val pct = (progress * 100).toInt().coerceIn(0, 100)
            val e = entries.getOrPut(taskId) { Entry(name, pct, text, paused = false) }
            e.name = name
            e.percent = pct
            e.text = text
            e.paused = false
            lastTaskId = taskId
            postTask(context, taskId, title = name, percent = pct, text = text, paused = false, segPoints = segPoints, segMarks = segMarks)
            instance?.refreshForeground()
        }

        /** 排队等待（fork.14 下载队列）：任务已建、等空槽；通知保留暂停/取消动作，不申请实况推广 */
        fun showQueued(context: Context, name: String, taskId: Long) {
            val e = entries.getOrPut(taskId) { Entry(name, -1, name, paused = true) }
            e.name = name
            e.text = name
            e.paused = true
            postTask(context, taskId, title = "等待下载", percent = -1, text = name, paused = true)
            instance?.refreshForeground()
        }

        /** 暂停：通知保留（可继续/取消），但不再申请实况推广 */
        fun updatePaused(context: Context, name: String, progress: Float, taskId: Long) {
            val pct = (progress * 100).toInt().coerceIn(0, 100)
            val e = entries.getOrPut(taskId) { Entry(name, pct, name, paused = true) }
            e.name = name
            e.percent = pct
            e.text = name
            e.paused = true
            postTask(context, taskId, title = "已暂停", percent = pct, text = name, paused = true)
            instance?.refreshForeground()
        }

        /** 完成：该任务的通知收尾为可点开/可划掉的结果通知；没有活跃任务时停服务 */
        fun finish(context: Context, text: String, taskId: Long) {
            entries.remove(taskId)
            postFinal(context, taskId, title = "下载完成", text = text, icon = android.R.drawable.stat_sys_download_done)
            afterTaskGone()
        }

        fun fail(context: Context, text: String, taskId: Long) {
            entries.remove(taskId)
            postFinal(context, taskId, title = "下载失败", text = text, icon = android.R.drawable.ic_dialog_alert)
            afterTaskGone()
        }

        /** 任务被删除：撤掉它的通知；没有活跃任务时停服务 */
        fun dismiss(context: Context, taskId: Long) {
            entries.remove(taskId)
            runCatching { manager(context).cancel(notifId(taskId)) }
            afterTaskGone()
        }

        private fun afterTaskGone() {
            if (entries.isEmpty()) instance?.stopNow() else instance?.refreshForeground()
        }

        // ---------- 通知构建 ----------

        private fun contentPending(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
            }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        /** 动作 PendingIntent：requestCode 由任务通知 ID 派生，保证多任务互不串扰（extras 不参与 PendingIntent 身份判定） */
        private fun actionPending(context: Context, taskId: Long, action: String, salt: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                notifId(taskId) * 4 + salt,
                Intent(context, DownloadActionReceiver::class.java)
                    .setAction(action)
                    .putExtra(DownloadActionReceiver.EXTRA_ID, taskId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun addTaskActions(
            context: Context,
            builder: NotificationCompat.Builder,
            taskId: Long,
            paused: Boolean,
        ) {
            val first = if (paused) {
                NotificationCompat.Action.Builder(
                    IconCompat.createWithResource(context, android.R.drawable.ic_media_play),
                    "继续",
                    actionPending(context, taskId, DownloadActionReceiver.ACTION_RESUME, 1),
                )
            } else {
                NotificationCompat.Action.Builder(
                    IconCompat.createWithResource(context, android.R.drawable.ic_media_pause),
                    "暂停",
                    actionPending(context, taskId, DownloadActionReceiver.ACTION_PAUSE, 1),
                )
            }
            builder.addAction(first.build())
            builder.addAction(
                NotificationCompat.Action.Builder(
                    IconCompat.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                    "取消",
                    actionPending(context, taskId, DownloadActionReceiver.ACTION_CANCEL, 2),
                ).build(),
            )
        }

        /** API 36+ 的实况进度样式；分段下载把分段表映射为 segments，已下完的段边界打 points */
        private fun progressStyle(
            context: Context,
            percent: Int,
            segPoints: List<Int>?,
            segMarks: List<Int>?,
        ): NotificationCompat.ProgressStyle {
            val style = NotificationCompat.ProgressStyle().setStyledByProgress(true)
            if (!segPoints.isNullOrEmpty()) {
                segPoints.forEach { style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(it)) }
            }
            if (!segMarks.isNullOrEmpty()) {
                segMarks.forEach { style.addProgressPoint(NotificationCompat.ProgressStyle.Point(it)) }
            }
            style.setProgress(percent)
            runCatching {
                style.setProgressTrackerIcon(
                    IconCompat.createWithResource(context, android.R.drawable.stat_sys_download),
                )
            }
            return style
        }

        private fun postTask(
            context: Context,
            taskId: Long,
            title: String,
            percent: Int,
            text: String,
            paused: Boolean,
            allowFloat: Boolean = false,
            segPoints: List<Int>? = null,
            segMarks: List<Int>? = null,
        ) {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(contentPending(context))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
            when {
                paused -> {
                    // 已暂停不是「进行中」，不申请实况推广；进度条用经典样式保证低版本一致
                    builder.setProgress(100, percent.coerceAtLeast(0), false)
                }
                percent < 0 -> {
                    builder.setRequestPromotedOngoing(true)
                    builder.setProgress(0, 0, true)
                }
                else -> {
                    // 实况通知（Live Updates）：promoted ongoing + ProgressStyle；API 36 以下自动落回经典进度条
                    builder.setRequestPromotedOngoing(true)
                    runCatching { builder.setShortCriticalText("$percent%") }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                        builder.setStyle(progressStyle(context, percent, segPoints, segMarks))
                    } else {
                        builder.setProgress(100, percent, false)
                    }
                }
            }
            addTaskActions(context, builder, taskId, paused)
            val notification = builder.build()
            attachIsland(context, notification, text, percent.coerceAtLeast(0), allowFloat, taskId)
            runCatching { manager(context).notify(notifId(taskId), notification) }
        }

        private fun postFinal(context: Context, taskId: Long, title: String, text: String, icon: Int) {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(icon)
                .setContentIntent(contentPending(context))
                .setOngoing(false)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            runCatching { manager(context).notify(notifId(taskId), notification) }
        }

        /** 前台服务的聚合摘要：单任务显示其名与进度，多任务显示数量与最近一个任务 */
        private fun buildSummary(context: Context): android.app.Notification {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentIntent(contentPending(context))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
            val snapshot = entries.entries.toList()
            if (snapshot.isEmpty()) {
                builder.setContentTitle("准备下载").setProgress(0, 0, true)
            } else {
                val primary = snapshot.firstOrNull { it.key == lastTaskId }?.value ?: snapshot.last().value
                val running = snapshot.count { !it.value.paused }
                builder.setContentTitle(
                    when {
                        snapshot.size == 1 && primary.paused -> "下载已暂停"
                        snapshot.size == 1 -> "正在下载"
                        running > 0 -> "正在下载"
                        else -> "下载已暂停"
                    },
                )
                builder.setContentText(
                    if (snapshot.size == 1) {
                        if (primary.percent >= 0) "${primary.name} · ${primary.percent}%" else primary.name
                    } else {
                        "${snapshot.size} 个任务 · ${primary.name}" +
                            if (primary.percent >= 0) " ${primary.percent}%" else ""
                    },
                )
                if (primary.percent >= 0) builder.setProgress(100, primary.percent, false)
                else builder.setProgress(0, 0, true)
            }
            return builder.build()
        }

        // ---------- 超级岛（小米焦点通知协议，非小米系统忽略） ----------

        /** OS3（超岛协议）才注入岛参数；更低版本走普通焦点通知 */
        private fun islandProtocol(context: Context): Int = runCatching {
            Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0)
        }.getOrDefault(0)

        private fun attachIsland(
            context: Context,
            notification: android.app.Notification,
            name: String,
            percent: Int,
            allowFloat: Boolean,
            taskId: Long,
        ) {
            if (islandProtocol(context) < 3) return
            runCatching {
                val float = allowFloat && islandFloated.add(taskId)
                notification.extras.putString("miui.focus.param", islandJson(name, percent, name, float))
                notification.extras.putString("miui.focus.business", "download")
            }
        }

        private fun islandJson(name: String, percent: Int, text: String, allowFloat: Boolean): String {
            val param = JSONObject()
                .put("protocol", 1)
                .put("business", "download")
                .put("enableFloat", allowFloat)
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
    }
}
