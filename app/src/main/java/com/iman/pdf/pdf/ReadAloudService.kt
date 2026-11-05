package com.iman.pdf.pdf

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
import androidx.core.content.ContextCompat
import com.iman.pdf.MainActivity

/**
 * Foreground service that keeps the app alive while a PDF is read aloud,
 * so reading continues when the screen is off or the app is in the background.
 * The speech itself is still done by [ReadAloud]; this only keeps the process running
 * and shows a notification with a Stop button.
 */
class ReadAloudService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val callback = onStopRequested
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            callback?.invoke()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "PDF" }
        val notification = buildNotification(title)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(title: String): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Read aloud", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, ReadAloudService::class.java).setAction(ACTION_STOP),
            flags
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Reading aloud")
            .setContentText(title)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "iman_read_aloud"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_STOP = "com.iman.pdf.READ_ALOUD_STOP"
        private const val EXTRA_TITLE = "title"

        /** Called when the user taps Stop in the notification. */
        @Volatile
        var onStopRequested: (() -> Unit)? = null

        fun start(context: Context, title: String) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ReadAloudService::class.java).putExtra(EXTRA_TITLE, title)
                )
            } catch (_: Exception) {
                // Reading still works in the foreground if the service cannot start.
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, ReadAloudService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
