package com.idlix.mobile.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.idlix.mobile.MainActivity
import com.idlix.mobile.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OfflineDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                val contentId = intent.getStringExtra(EXTRA_CONTENT_ID)
                if (!contentId.isNullOrBlank()) {
                    OfflineDownloadManager.pauseDownload(this, contentId)
                }
            }
            ACTION_CANCEL -> {
                val contentId = intent.getStringExtra(EXTRA_CONTENT_ID)
                if (!contentId.isNullOrBlank()) {
                    OfflineDownloadManager.cancelAndRemoveDownload(this, contentId)
                }
            }
        }

        val initialNotification = buildProgressNotification(
            title = getString(R.string.download_notification_preparing),
            subtitle = getString(R.string.download_status_resolving),
            progress = 0,
            indeterminate = true,
            activeContentId = null
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (_: Exception) {
        }

        startMonitoring()
        return START_NOT_STICKY
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        val store = OfflineDownloadStore.getInstance(this)
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        monitorJob = serviceScope.launch {
            store.downloadsFlow.collect { items ->
                val activeItems = items.filter { it.isActive }
                if (activeItems.isEmpty()) {
                    delay(800)
                    if (!OfflineDownloadManager.hasActiveJobs()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                } else {
                    val primary = activeItems.first()
                    val isResolving = primary.status == DownloadStatus.RESOLVING ||
                        primary.status == DownloadStatus.QUEUED
                    val extraCount = activeItems.size - 1
                    val titleText = if (extraCount > 0) {
                        "${primary.title} (+$extraCount)"
                    } else {
                        primary.title
                    }
                    val subText = if (isResolving) {
                        getString(R.string.download_status_resolving)
                    } else {
                        "${primary.progressPercent}% • ${primary.formattedSize}"
                    }

                    val notification = buildProgressNotification(
                        title = titleText,
                        subtitle = subText,
                        progress = primary.progressPercent,
                        indeterminate = isResolving,
                        activeContentId = primary.contentId
                    )
                    try {
                        notificationManager?.notify(NOTIFICATION_ID, notification)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun buildProgressNotification(
        title: String,
        subtitle: String,
        progress: Int,
        indeterminate: Boolean,
        activeContentId: String?
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress.coerceIn(0, 100), indeterminate)

        if (!activeContentId.isNullOrBlank()) {
            val pauseIntent = Intent(this, OfflineDownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_CONTENT_ID, activeContentId)
            }
            val pausePending = PendingIntent.getService(
                this,
                activeContentId.hashCode(),
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.download_pause),
                pausePending
            )

            val cancelIntent = Intent(this, OfflineDownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_CONTENT_ID, activeContentId)
            }
            val cancelPending = PendingIntent.getService(
                this,
                activeContentId.hashCode() + 1,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.cancel),
                cancelPending
            )
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.download_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "idlix_mobile_offline_downloads"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_PAUSE = "com.idlix.mobile.download.ACTION_PAUSE"
        private const val ACTION_CANCEL = "com.idlix.mobile.download.ACTION_CANCEL"
        private const val EXTRA_CONTENT_ID = "extra_content_id"

        fun start(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, OfflineDownloadService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            } catch (_: Exception) {
            }
        }
    }
}
