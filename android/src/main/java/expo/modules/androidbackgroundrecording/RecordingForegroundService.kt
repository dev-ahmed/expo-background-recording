package expo.modules.androidbackgroundrecording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class RecordingForegroundService : Service() {
  private val TAG = "RecordingService"
  private val NOTIFICATION_ID = 12345
  private val CHANNEL_ID = "recording_channel"

  companion object {
    const val ACTION_START = "ACTION_START_RECORDING"
    const val ACTION_STOP = "ACTION_STOP_RECORDING"
  }

  override fun onCreate() {
    super.onCreate()
    Log.d(TAG, "Service created")
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START -> {
        Log.d(TAG, "Starting foreground service")
        startForeground(NOTIFICATION_ID, createNotification())
      }
      ACTION_STOP -> {
        Log.d(TAG, "Stopping foreground service")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
      }
    }
    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Recording Service",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Used for background audio recording"
        setSound(null, null)
      }

      val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
      Log.d(TAG, "Notification channel created")
    }
  }

  private fun createNotification(): Notification {
    val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      notificationIntent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("Recording in progress")
      .setContentText("Audio is being recorded")
      .setSmallIcon(android.R.drawable.ic_btn_speak_now)
      .setContentIntent(pendingIntent)
      .setOngoing(true)
      .setSilent(true)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .build()
  }

  override fun onDestroy() {
    Log.d(TAG, "Service destroyed")
    super.onDestroy()
  }
}
