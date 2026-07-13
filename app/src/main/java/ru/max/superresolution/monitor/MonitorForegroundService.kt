package ru.max.superresolution.monitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MonitorForegroundService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var pollJob: Job? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    NotificationHelper.ensureChannels(this)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopMonitoring()
        return START_NOT_STICKY
      }
      ACTION_REFRESH -> {
        if (!shouldRun()) {
          stopMonitoring()
          return START_NOT_STICKY
        }
        ensureForeground()
        scope.launch { pollOnce() }
        return START_STICKY
      }
    }

    if (!shouldRun()) {
      stopMonitoring()
      return START_NOT_STICKY
    }

    ensureForeground()
    if (pollJob?.isActive != true) {
      pollJob = scope.launch { pollLoop() }
    }
    return START_STICKY
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    stopMonitoring()
    super.onTaskRemoved(rootIntent)
  }

  override fun onDestroy() {
    pollJob?.cancel()
    scope.cancel()
    super.onDestroy()
  }

  private fun shouldRun(): Boolean {
    val config = MonitorEngine.loadConfig(this)
    return config.host.isNotBlank() && config.notificationsEnabled
  }

  private fun stopMonitoring() {
    pollJob?.cancel()
    pollJob = null
    MonitorEngine.resetTransitionState()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun ensureForeground() {
    startForeground(
      NOTIFICATION_FOREGROUND_ID,
      NotificationHelper.foregroundNotification(this),
    )
  }

  private suspend fun pollLoop() {
    while (scope.isActive) {
      if (!shouldRun()) {
        stopMonitoring()
        return
      }
      pollOnce()
      delay(MonitorPrefs.POLL_INTERVAL_MS)
    }
  }

  private suspend fun pollOnce() {
    if (!shouldRun()) {
      stopMonitoring()
      return
    }
    MonitorEngine.poll(this)
  }

  companion object {
    private const val ACTION_STOP = "ru.max.superresolution.monitor.STOP"
    private const val ACTION_REFRESH = "ru.max.superresolution.monitor.REFRESH"
    private const val NOTIFICATION_FOREGROUND_ID = 1001

    fun start(context: Context) {
      val intent = Intent(context, MonitorForegroundService::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }

    fun stop(context: Context) {
      val intent = Intent(context, MonitorForegroundService::class.java).apply {
        action = ACTION_STOP
      }
      context.startService(intent)
    }

    fun requestRefresh(context: Context) {
      val config = MonitorEngine.loadConfig(context)
      if (!config.notificationsEnabled) return
      val intent = Intent(context, MonitorForegroundService::class.java).apply {
        action = ACTION_REFRESH
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
      } else {
        context.startService(intent)
      }
    }
  }
}
