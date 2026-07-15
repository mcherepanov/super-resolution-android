package ru.max.superresolution.monitor

data class MonitorUiState(
  val isOnline: Boolean? = null,
  val currentJob: CurrentJob? = null,
  val workersBusy: Int = 0,
  val queueSize: Int? = null,
  val doneToday: Int? = null,
  val doneYesterday: Int? = null,
  val readyDownloads: Int? = null,
  val updatedAt: String? = null,
  val appVersion: String? = null,
  val appBuild: Int? = null,
  val lastError: String? = null,
)
