package ru.max.superresolution.monitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {
  private const val CHANNEL_DONE = "queue_done"
  private const val CHANNEL_FOREGROUND = "monitor_active"
  private const val NOTIFICATION_DONE_ID = 1
  private const val NOTIFICATION_FOREGROUND_ID = 2
  private const val ALERT_DURATION_MS = 5_000L

  fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return

    val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    val attrs = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_ALARM)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .build()

    val doneChannel = NotificationChannel(
      CHANNEL_DONE,
      "Завершение обработки",
      NotificationManager.IMPORTANCE_HIGH,
    ).apply {
      description = "Уведомление, когда все задачи в очереди выполнены"
      enableVibration(true)
      vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 600)
      setSound(alarmSound, attrs)
    }

    val fgChannel = NotificationChannel(
      CHANNEL_FOREGROUND,
      "Фоновый мониторинг",
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      description = "Показывает, что приложение следит за очередью"
      setShowBadge(false)
    }

    manager.createNotificationChannel(doneChannel)
    manager.createNotificationChannel(fgChannel)
  }

  fun canPost(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
  }

  fun foregroundNotification(context: Context): android.app.Notification {
    val open = PendingIntent.getActivity(
      context,
      0,
      Intent(context, MainActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
      .setSmallIcon(android.R.drawable.ic_menu_info_details)
      .setContentTitle("SR Monitor")
      .setContentText("Слежение за очередью активно")
      .setOngoing(true)
      .setContentIntent(open)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  fun notifyQueueDone(context: Context) {
    ensureChannels(context)
    if (!canPost(context)) return

    val open = PendingIntent.getActivity(
      context,
      0,
      Intent(context, MainActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle("Очередь пуста")
      .setContentText("Все задачи обработаны!")
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
      .setAutoCancel(true)
      .setContentIntent(open)
      .build()

    NotificationManagerCompat.from(context).notify(NOTIFICATION_DONE_ID, notification)
    playLongAlert(context)
  }

  private fun playLongAlert(context: Context) {
    try {
      val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      val player = MediaPlayer.create(context, uri) ?: return
      player.setOnCompletionListener { it.release() }
      player.start()
      Handler(Looper.getMainLooper()).postDelayed({
        if (player.isPlaying) {
          player.stop()
        }
        player.release()
      }, ALERT_DURATION_MS)
    } catch (_: Exception) {
    }
  }
}
