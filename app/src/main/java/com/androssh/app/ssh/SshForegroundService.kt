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
import com.androssh.app.AndroSshApplication
import com.androssh.app.MainActivity
import com.androssh.app.R

/**
 * Keeps the app process alive (and visible to the user through an ongoing notification) while an
 * SSH shell session is open, so Android does not freeze or kill the process - and with it the
 * socket and the output-reading coroutine - once the app goes to the background.
 *
 * The live session itself is owned by the process-scoped [SshSessionHolder], which outlives the
 * Activity/ViewModel; this service is what keeps that process (and therefore the session) around.
 * The notification shows `user@host`, a ticking elapsed time, returns to the live terminal when
 * tapped, and offers a Disconnect action that closes the session and stops the service.
 */
class SshForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            (application as? AndroSshApplication)?.container?.sshSessionHolder?.disconnect()
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        val label = intent?.getStringExtra(EXTRA_LABEL).orEmpty()
        val startedAt = intent?.getLongExtra(EXTRA_STARTED_AT, 0L)?.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        createNotificationChannel()
        // Starting the service again for a new session simply updates this single notification,
        // so reconnecting (or connecting to a different host) cannot leak a second one.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(label, startedAt),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
        // A session cannot be re-established without the user's input, so there is no point in
        // letting the system restart this service after a process death.
        return START_NOT_STICKY
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Low-importance channel so the ongoing notification never makes a sound or heads-up popup.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.ssh_session_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.ssh_session_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(label: String, startedAt: Long): Notification {
        // No profile id extra here on purpose: the session already lives in SshSessionHolder, so
        // returning to the app must resume it instead of opening a second connection.
        val contentIntent = PendingIntent.getActivity(
            this,
            REQUEST_CONTENT,
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnectIntent = PendingIntent.getService(
            this,
            REQUEST_DISCONNECT,
            Intent(this, SshForegroundService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (label.isBlank()) {
            getString(R.string.ssh_session_notification_title)
        } else {
            getString(R.string.ssh_session_notification_running, label)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentIntent(contentIntent)
            // Renders the ticking "N minutes" elapsed time seen in the shade.
            .setWhen(startedAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.ssh_session_notification_disconnect),
                disconnectIntent,
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ssh_session"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CONTENT = 0
        private const val REQUEST_DISCONNECT = 1
        private const val EXTRA_LABEL = "com.androssh.app.EXTRA_LABEL"
        private const val EXTRA_STARTED_AT = "com.androssh.app.EXTRA_STARTED_AT"
        private const val ACTION_DISCONNECT = "com.androssh.app.action.DISCONNECT"

        /**
         * Starts (or updates) the ongoing notification for the session described by [label], whose
         * elapsed time is counted from [startedAtMillis].
         */
        fun start(context: Context, label: String, startedAtMillis: Long) {
            val intent = Intent(context, SshForegroundService::class.java)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_STARTED_AT, startedAtMillis)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Stops the service and removes its notification; safe to call when not running. */
        fun stop(context: Context) {
            context.stopService(Intent(context, SshForegroundService::class.java))
        }
    }
}

/**
 * Abstraction over [SshForegroundService] so [SshSessionHolder] can anchor the process lifetime
 * without depending on a [Context] directly (and so it stays unit-testable).
 */
interface SshSessionKeepAlive {
    fun start(label: String, startedAtMillis: Long)

    fun stop()
}

class ForegroundServiceKeepAlive(context: Context) : SshSessionKeepAlive {
    private val appContext = context.applicationContext

    /**
     * Starting the service again for a new session simply updates the single existing notification,
     * so reconnecting cannot leak multiple services/notifications.
     */
    override fun start(label: String, startedAtMillis: Long) =
        SshForegroundService.start(appContext, label, startedAtMillis)

    override fun stop() = SshForegroundService.stop(appContext)
}
