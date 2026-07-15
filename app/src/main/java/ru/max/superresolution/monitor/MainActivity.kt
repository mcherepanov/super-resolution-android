package ru.max.superresolution.monitor

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppDestination(val title: String, val icon: ImageVector) {
  Monitor("Монитор", Icons.Filled.MonitorHeart),
  Files("Файлы", Icons.Filled.Folder),
  Jobs("Задачи", Icons.AutoMirrored.Filled.ListAlt),
  Settings("Настройки", Icons.Filled.Settings),
  About("О приложении", Icons.Filled.Info),
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    NotificationHelper.ensureChannels(this)
    setContent {
      val prefs = remember { getSharedPreferences(MonitorPrefs.NAME, Context.MODE_PRIVATE) }
      var darkTheme by remember {
        mutableStateOf(prefs.getBoolean(MonitorPrefs.KEY_DARK_THEME, false))
      }
      AppTheme(darkTheme = darkTheme) {
        MonitorScreen(
          darkTheme = darkTheme,
          onDarkThemeChange = { enabled ->
            darkTheme = enabled
            prefs.edit().putBoolean(MonitorPrefs.KEY_DARK_THEME, enabled).apply()
          },
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitorScreen(
  darkTheme: Boolean,
  onDarkThemeChange: (Boolean) -> Unit,
) {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences(MonitorPrefs.NAME, Context.MODE_PRIVATE) }
  val scope = rememberCoroutineScope()
  val uiState by MonitorRepository.uiState.collectAsState()
  val drawerState = rememberDrawerState(DrawerValue.Closed)

  var host by remember { mutableStateOf(prefs.getString(MonitorPrefs.KEY_HOST, "") ?: "") }
  var port by remember {
    mutableStateOf(prefs.getString(MonitorPrefs.KEY_PORT, MonitorPrefs.DEFAULT_PORT) ?: MonitorPrefs.DEFAULT_PORT)
  }
  var username by remember { mutableStateOf(prefs.getString(MonitorPrefs.KEY_USER, "") ?: "") }
  var password by remember { mutableStateOf(prefs.getString(MonitorPrefs.KEY_PASS, "") ?: "") }
  var notificationsEnabled by remember {
    mutableStateOf(prefs.getBoolean(MonitorPrefs.KEY_NOTIFY, true))
  }
  var isManualRefreshing by remember { mutableStateOf(false) }
  var destination by remember { mutableStateOf(AppDestination.Monitor) }
  var showOnboarding by remember {
    mutableStateOf(
      !prefs.getBoolean(MonitorPrefs.KEY_ONBOARDING_DONE, false) &&
        (prefs.getString(MonitorPrefs.KEY_HOST, "").orEmpty()).isBlank(),
    )
  }

  fun markOnboardingDone() {
    prefs.edit().putBoolean(MonitorPrefs.KEY_ONBOARDING_DONE, true).apply()
    showOnboarding = false
  }

  val connectionConfig = ConnectionConfig(
    host = host,
    port = port,
    username = username,
    password = password,
    notificationsEnabled = notificationsEnabled,
  )

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { granted ->
    if (!granted) {
      notificationsEnabled = false
      prefs.edit().putBoolean(MonitorPrefs.KEY_NOTIFY, false).apply()
      Toast.makeText(context, "Разрешите уведомления в настройках Android", Toast.LENGTH_LONG).show()
    }
  }

  val notifyPermissionOk = NotificationHelper.canPost(context)

  fun persistSettings() {
    prefs.edit()
      .putString(MonitorPrefs.KEY_HOST, host.trim())
      .putString(MonitorPrefs.KEY_PORT, port.trim())
      .putString(MonitorPrefs.KEY_USER, username)
      .putString(MonitorPrefs.KEY_PASS, password)
      .putBoolean(MonitorPrefs.KEY_NOTIFY, notificationsEnabled)
      .apply()
  }

  fun onNotificationsToggle(enabled: Boolean) {
    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (!NotificationHelper.canPost(context)) {
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
    notificationsEnabled = enabled
    prefs.edit().putBoolean(MonitorPrefs.KEY_NOTIFY, enabled).apply()
    if (!enabled) {
      MonitorForegroundService.stop(context)
    }
  }

  LaunchedEffect(notificationsEnabled) {
    if (
      notificationsEnabled &&
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      !NotificationHelper.canPost(context)
    ) {
      permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  LaunchedEffect(host, port, username, password, notificationsEnabled) {
    persistSettings()
    if (host.trim().isNotEmpty() && notificationsEnabled) {
      MonitorForegroundService.start(context)
    } else {
      MonitorForegroundService.stop(context)
    }
  }

  LaunchedEffect(uiState.updatedAt, uiState.lastError, uiState.isOnline) {
    if (isManualRefreshing) {
      isManualRefreshing = false
    }
  }

  fun refresh() {
    if (host.trim().isEmpty()) {
      Toast.makeText(context, "Укажите адрес сервера в Настройках", Toast.LENGTH_SHORT).show()
      destination = AppDestination.Settings
      return
    }
    persistSettings()
    isManualRefreshing = true
    if (notificationsEnabled) {
      MonitorForegroundService.requestRefresh(context)
    } else {
      scope.launch {
        withContext(Dispatchers.IO) { MonitorEngine.poll(context) }
        isManualRefreshing = false
      }
      return
    }
    scope.launch {
      delay(5_000)
      isManualRefreshing = false
    }
  }

  if (showOnboarding) {
    AlertDialog(
      onDismissRequest = { markOnboardingDone() },
      title = { Text("Подключение к серверу") },
      text = {
        Text(
          "Укажите IP-адрес сервера Super Resolution в той же Wi‑Fi сети " +
            "или при доступе через VPN. Порт по умолчанию — 8080.",
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            markOnboardingDone()
            destination = AppDestination.Settings
          },
        ) {
          Text("К настройкам")
        }
      },
      dismissButton = {
        TextButton(onClick = { markOnboardingDone() }) {
          Text("Позже")
        }
      },
    )
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    drawerContent = {
      ModalDrawerSheet {
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
          Text(
            text = "Super Resolution",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
          )
          HorizontalDivider()
          Spacer(modifier = Modifier.height(8.dp))
          AppDestination.entries.forEach { item ->
            NavigationDrawerItem(
              label = { Text(item.title) },
              selected = destination == item,
              onClick = {
                destination = item
                scope.launch { drawerState.close() }
              },
              icon = { Icon(item.icon, contentDescription = item.title) },
              colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              ),
            )
          }
        }
      }
    },
  ) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Column {
              Text("Super Resolution", fontWeight = FontWeight.Bold)
              Text(
                destination.title,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.88f),
              )
            }
          },
          navigationIcon = {
            IconButton(onClick = { scope.launch { drawerState.open() } }) {
              Icon(
                imageVector = if (drawerState.isOpen) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu,
                contentDescription = "Меню",
                tint = Color.White,
              )
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SrBlueDeep,
            titleContentColor = Color.White,
          ),
        )
      },
    ) { padding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(horizontal = 16.dp, vertical = 12.dp),
      ) {
        when (destination) {
          AppDestination.Monitor -> MonitorDestination(
            uiState = uiState,
            isManualRefreshing = isManualRefreshing,
            onRefresh = { refresh() },
          )
          AppDestination.Files -> FilesTab(
            config = connectionConfig.copy(host = host.trim()),
            isVisible = destination == AppDestination.Files,
          )
          AppDestination.Jobs -> JobsTab(
            config = connectionConfig.copy(host = host.trim()),
            isVisible = destination == AppDestination.Jobs,
          )
          AppDestination.Settings -> SettingsDestination(
            host = host,
            onHostChange = { host = it },
            port = port,
            onPortChange = { port = it },
            username = username,
            onUsernameChange = { username = it },
            password = password,
            onPasswordChange = { password = it },
            notificationsEnabled = notificationsEnabled,
            onNotificationsToggle = { onNotificationsToggle(it) },
            notifyPermissionOk = notifyPermissionOk,
            darkTheme = darkTheme,
            onDarkThemeChange = onDarkThemeChange,
            isManualRefreshing = isManualRefreshing,
            onRefresh = { refresh() },
          )
          AppDestination.About -> AboutDestination(uiState = uiState)
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun MonitorDestination(
  uiState: MonitorUiState,
  isManualRefreshing: Boolean,
  onRefresh: () -> Unit,
) {
  val pullState = rememberPullRefreshState(
    refreshing = isManualRefreshing,
    onRefresh = onRefresh,
  )
  Box(
    modifier = Modifier
      .fillMaxSize()
      .pullRefresh(pullState),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      InfoCard(
        isOnline = uiState.isOnline,
        currentJob = uiState.currentJob,
        workersBusy = uiState.workersBusy,
        queueSize = uiState.queueSize,
        doneToday = uiState.doneToday,
        doneYesterday = uiState.doneYesterday,
        readyDownloads = uiState.readyDownloads,
        updatedAt = uiState.updatedAt,
      )
      uiState.lastError?.let { error ->
        Text(
          text = error,
          style = MaterialTheme.typography.bodyMedium,
          color = SrErrorRed,
        )
      }
      Button(
        onClick = onRefresh,
        enabled = !isManualRefreshing,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
          containerColor = SrBlueDeep,
          contentColor = Color.White,
        ),
      ) {
        Text(if (isManualRefreshing) "Загрузка…" else "Обновить")
      }
    }
    PullRefreshIndicator(
      refreshing = isManualRefreshing,
      state = pullState,
      modifier = Modifier.align(Alignment.TopCenter),
      contentColor = SrOrange,
    )
  }
}

@Composable
private fun SettingsDestination(
  host: String,
  onHostChange: (String) -> Unit,
  port: String,
  onPortChange: (String) -> Unit,
  username: String,
  onUsernameChange: (String) -> Unit,
  password: String,
  onPasswordChange: (String) -> Unit,
  notificationsEnabled: Boolean,
  onNotificationsToggle: (Boolean) -> Unit,
  notifyPermissionOk: Boolean,
  darkTheme: Boolean,
  onDarkThemeChange: (Boolean) -> Unit,
  isManualRefreshing: Boolean,
  onRefresh: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    SettingsCard(
      host = host,
      onHostChange = onHostChange,
      port = port,
      onPortChange = onPortChange,
      username = username,
      onUsernameChange = onUsernameChange,
      password = password,
      onPasswordChange = onPasswordChange,
      notificationsEnabled = notificationsEnabled,
      onNotificationsToggle = onNotificationsToggle,
      notifyPermissionOk = notifyPermissionOk,
      isManualRefreshing = isManualRefreshing,
      onRefresh = onRefresh,
    )

    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Filled.DarkMode, contentDescription = null, tint = SrOrange)
          Column {
            Text("Тёмная тема", fontWeight = FontWeight.SemiBold)
            Text(
              if (darkTheme) "Включена" else "Выключена",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Switch(checked = darkTheme, onCheckedChange = onDarkThemeChange)
      }
    }
  }
}

@Composable
private fun AboutDestination(uiState: MonitorUiState) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("SR Monitor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
          "Клиент для self-hosted Super Resolution",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        Text("Клиент", fontWeight = FontWeight.SemiBold)
        Text("Версия: ${BuildConfig.VERSION_NAME}")
        Text("Сборка: ${BuildConfig.VERSION_CODE}")
        HorizontalDivider()
        Text("Сервер", fontWeight = FontWeight.SemiBold)
        val serverLabel = when {
          uiState.appVersion != null && uiState.appBuild != null ->
            "${uiState.appVersion} · ${uiState.appBuild}"
          uiState.appVersion != null -> uiState.appVersion
          uiState.isOnline == true -> "онлайн (версия не передана)"
          uiState.isOnline == false -> "недоступен"
          else -> "—"
        }
        Text("Версия: $serverLabel")
        HorizontalDivider()
        Text(
          text = "© @MaxCherepanov",
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun InfoCard(
  isOnline: Boolean?,
  currentJob: CurrentJob?,
  workersBusy: Int,
  queueSize: Int?,
  doneToday: Int?,
  doneYesterday: Int?,
  readyDownloads: Int?,
  updatedAt: String?,
) {
  val statusColor = when (isOnline) {
    true -> SrDoneGreen
    false -> SrErrorRed
    null -> MaterialTheme.colorScheme.onSurfaceVariant
  }
  val statusLabel = when (isOnline) {
    true -> "ONLINE"
    false -> "OFFLINE"
    null -> "—"
  }
  val trackColor = MaterialTheme.colorScheme.outlineVariant

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Статус",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = statusLabel,
          color = statusColor,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
      }

      if (currentJob != null) {
        val job = currentJob
        val pct = (job.progressPct ?: 0.0).coerceIn(0.0, 100.0)
        val inputFmt = job.inputFormat?.uppercase() ?: "?"
        val outputFmt = (job.outputFormat ?: "wav").uppercase()

        Text(
          text = job.filename,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "$inputFmt → $outputFmt",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        job.optionsSummary?.let { summary ->
          Text(
            text = "Обработка: $summary",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        job.progressDetail?.let { detail ->
          Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = SrOrange,
          )
        }
        LinearProgressIndicator(
          progress = { (pct / 100.0).toFloat() },
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
          color = SrOrange,
          trackColor = trackColor,
        )
        Text(
          text = "${pct.toInt()}%",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Medium,
        )
      } else if (workersBusy > 0) {
        Text(
          text = "Идёт обработка…",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = SrOrange,
        )
        Text(
          text = "Детали трека появятся после обновления сервера",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
          color = SrOrange,
          trackColor = trackColor,
        )
      } else {
        Text(
          text = if (isOnline == true) "Нет активной обработки" else "—",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      Text("В очереди: ${queueSize?.toString() ?: "—"}")
      Text("К скачиванию: ${readyDownloads?.toString() ?: "—"}")
      Text("Готово сегодня: ${doneToday?.toString() ?: "—"}")
      Text("Готово вчера: ${doneYesterday?.toString() ?: "—"}")

      updatedAt?.let {
        Text(
          text = "Обновлено: $it",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun SettingsCard(
  host: String,
  onHostChange: (String) -> Unit,
  port: String,
  onPortChange: (String) -> Unit,
  username: String,
  onUsernameChange: (String) -> Unit,
  password: String,
  onPasswordChange: (String) -> Unit,
  notificationsEnabled: Boolean,
  onNotificationsToggle: (Boolean) -> Unit,
  notifyPermissionOk: Boolean,
  isManualRefreshing: Boolean,
  onRefresh: () -> Unit,
) {
  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    cursorColor = MaterialTheme.colorScheme.primary,
  )

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = "Подключение",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )

      OutlinedTextField(
        value = host,
        onValueChange = onHostChange,
        label = { Text("Адрес сервера") },
        placeholder = { Text("IP или hostname") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = fieldColors,
      )
      OutlinedTextField(
        value = port,
        onValueChange = onPortChange,
        label = { Text("Порт") },
        placeholder = { Text(MonitorPrefs.DEFAULT_PORT) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = fieldColors,
      )
      OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text("Логин") },
        placeholder = { Text("admin") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = fieldColors,
      )
      OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Пароль") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = fieldColors,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Уведомления при завершении")
        Switch(
          checked = notificationsEnabled,
          onCheckedChange = onNotificationsToggle,
        )
      }
      if (notificationsEnabled && !notifyPermissionOk) {
        Text(
          text = "Нет разрешения Android — откройте Настройки → Приложения → SR Monitor → Уведомления",
          style = MaterialTheme.typography.bodySmall,
          color = SrErrorRed,
        )
      }
      Text(
        text = "Фоновый мониторинг — только при включённых уведомлениях. " +
          "Чтобы остановить: выключите переключатель или смахните приложение из списка недавних.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Button(
        onClick = onRefresh,
        enabled = !isManualRefreshing,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
          containerColor = SrBlueDeep,
          contentColor = Color.White,
        ),
      ) {
        Text(if (isManualRefreshing) "Загрузка…" else "Обновить")
      }
    }
  }
}
