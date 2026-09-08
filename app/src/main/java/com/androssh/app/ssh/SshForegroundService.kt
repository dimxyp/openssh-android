package com.androssh.app.ssh

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
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.androssh.app.MainActivity
import com.androssh.app.R

/**
 * Keeps the app process alive (and visible to the user through a notification) while an SSH shell
 * session is open, so Android does not freeze or kill the process - and with it the socket and the
 * output-reading coroutine - once the app goes to the background.
 *
 * Tradeoff: the service deliberately does *not* own the [ActiveSshSession]. The session stays in
 * [com.androssh.app.viewmodel.AndroSshViewModel], and the service is only a lightweight lifecycle
 * anchor. That keeps the existing architecture untouched at the cost of the session still being
 * bound to the view model's lifetime: if the process is killed anyway (e.g. by the user swiping the
 * task away, or by a low-memory kill), the connection is lost rather than being restored.
 */
class SshForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_LABEL).orEmpty()
        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(label),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
        // The session cannot be recreated without the view model state, so there is no point in
        // letting the system restart this service after a process death.
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.ssh_session_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun buildNotification(label: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(getString(R.string.ssh_session_notification_title))
            .setContentText(
                if (label.isBlank()) {
                    getString(R.string.ssh_session_notification_title)
                } else {
                    getString(R.string.ssh_session_notification_text, label)
                },
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ssh_session"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_LABEL = "com.androssh.app.EXTRA_LABEL"

        /** Starts (or updates) the keep-alive notification for the session described by [label]. */
        fun start(context: Context, label: String) {
            val intent = Intent(context, SshForegroundService::class.java)
                .putExtra(EXTRA_LABEL, label)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Stops the service and removes its notification; safe to call when not running. */
        fun stop(context: Context) {
            context.stopService(Intent(context, SshForegroundService::class.java))
        }
    }
}

/**
 * Abstraction over [SshForegroundService] so the view model can anchor the process lifetime without
 * depending on a [Context] directly (and so it stays unit-testable).
 */
interface SshSessionKeepAlive {
    fun start(label: String)

    fun stop()
}

class ForegroundServiceKeepAlive(context: Context) : SshSessionKeepAlive {
    private val appContext = context.applicationContext

    /**
     * Starting the service again for a new session simply updates the single existing notification,
     * so reconnecting cannot leak multiple services/notifications.
     */
    override fun start(label: String) = SshForegroundService.start(appContext, label)

    override fun stop() = SshForegroundService.stop(appContext)
}
