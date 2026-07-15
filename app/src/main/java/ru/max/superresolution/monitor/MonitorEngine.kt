package ru.max.superresolution.monitor

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object MonitorEngine {
  private var previousWorkActive: Boolean? = null
  private var lastDoneToday: Int? = null

  fun resetTransitionState() {
    previousWorkActive = null
    lastDoneToday = null
  }

  fun loadConfig(context: Context): ConnectionConfig {
    val prefs = context.getSharedPreferences(MonitorPrefs.NAME, Context.MODE_PRIVATE)
    return ConnectionConfig(
      host = prefs.getString(MonitorPrefs.KEY_HOST, "")?.trim().orEmpty(),
      port = prefs.getString(MonitorPrefs.KEY_PORT, MonitorPrefs.DEFAULT_PORT)
        ?: MonitorPrefs.DEFAULT_PORT,
      username = prefs.getString(MonitorPrefs.KEY_USER, "") ?: "",
      password = prefs.getString(MonitorPrefs.KEY_PASS, "") ?: "",
      notificationsEnabled = prefs.getBoolean(MonitorPrefs.KEY_NOTIFY, true),
    )
  }

  suspend fun poll(context: Context): Boolean {
    val config = loadConfig(context)
    if (config.host.isBlank()) return false

    return try {
      val api = NetworkClient.createApi(config.host, config.port, config.username, config.password)
      val tz = TimeZone.getDefault().id
      applyResponse(context, api.getMobileStatus(tz), config.notificationsEnabled)
      true
    } catch (e: Exception) {
      MonitorRepository.update(
        MonitorUiState(
          isOnline = false,
          workersBusy = 0,
          queueSize = null,
          doneToday = null,
          doneYesterday = null,
          readyDownloads = null,
          updatedAt = null,
          appVersion = null,
          appBuild = null,
          lastError = ApiErrors.userMessage(e),
        ),
      )
      false
    }
  }

  private fun applyResponse(
    context: Context,
    response: StatusResponse,
    notificationsEnabled: Boolean,
  ) {
    val queue = response.queueSize
    val busy = response.workersBusy
    val workNow = queue > 0 || busy > 0
    val done = response.tasksCompletedToday ?: 0
    val doneYesterday = response.tasksCompletedYesterday
    val ready = response.readyDownloads

    if (notificationsEnabled) {
      val workEnded = previousWorkActive == true && !workNow
      val doneIncreased = lastDoneToday != null && done > lastDoneToday!! && !workNow
      if (workEnded || doneIncreased) {
        NotificationHelper.notifyQueueDone(context)
      }
    }

    previousWorkActive = workNow
    lastDoneToday = done

    MonitorRepository.update(
      MonitorUiState(
        isOnline = response.status == "ok",
        currentJob = response.currentJob,
        workersBusy = busy,
        queueSize = queue,
        doneToday = done,
        doneYesterday = doneYesterday,
        readyDownloads = ready,
        updatedAt = formatTimestamp(response.timestamp),
        appVersion = response.appVersion,
        appBuild = response.appBuild,
        lastError = null,
      ),
    )
  }

  private fun formatTimestamp(timestamp: Long): String {
    val millis = if (timestamp > 1_000_000_000_000L) timestamp else timestamp * 1000L
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(millis))
  }
}
