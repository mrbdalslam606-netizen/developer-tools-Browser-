package com.unixshells.devbrowser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Keeps the browser process in the foreground without owning Activity or View
 * references. Browser runtime ownership is added incrementally by the runtime
 * separation phase; this service remains a lifecycle coordinator.
 */
class BrowserForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.unixshells.devbrowser.action.START_RUNTIME"
        const val ACTION_STOP = "com.unixshells.devbrowser.action.STOP_RUNTIME"

        private const val CHANNEL_ID = "browser_runtime"
        private const val NOTIFICATION_ID = 1001

        fun startIntent(service: Service): Intent =
            Intent(service, BrowserForegroundService::class.java).setAction(ACTION_START)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground()
        BrowserRuntime.start(
            applicationContext,
            getSharedPreferences("devbrowser_settings", MODE_PRIVATE)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        // START_STICKY lets Android recreate the coordinator after a process kill.
        // The recreated runtime will be restored by BrowserRuntime/SessionRepository.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        BrowserRuntime.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_devtools)
            .setContentTitle("Developer Tools Browser")
            .setContentText("Browser runtime active")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Browser runtime",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the developer browser runtime available in the background"
                setShowBadge(false)
            }
        )
    }
}
