package ru.max.superresolution.monitor

object MonitorPrefs {
  const val NAME = "monitor_prefs"
  const val KEY_HOST = "host"
  const val KEY_PORT = "port"
  const val KEY_USER = "user"
  const val KEY_PASS = "pass"
  const val KEY_NOTIFY = "notify_enabled"
  const val KEY_DARK_THEME = "dark_theme"
  const val DEFAULT_PORT = "8080"
  const val POLL_INTERVAL_MS = 3_000L
}

data class ConnectionConfig(
  val host: String,
  val port: String,
  val username: String,
  val password: String,
  val notificationsEnabled: Boolean,
)
