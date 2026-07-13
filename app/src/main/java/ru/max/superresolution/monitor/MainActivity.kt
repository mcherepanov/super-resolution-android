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
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    NotificationHelper.ensureChannels(this)
    setContent {
      AppTheme {
        MonitorScreen()
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitorScreen() {
  val context = LocalContext.current
  val prefs = remember { context.getSharedPreferences(MonitorPrefs.NAME, Context.MODE_PRIVATE) }
  val scope = rememberCoroutineScope()
  val uiState by MonitorRepository.uiState.collectAsState()

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
  var selectedTab by remember { mutableIntStateOf(0) }

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

  LaunchedEffect(uiState.updatedAt) {
    if (isManualRefreshing) {
      isManualRefreshing = false
    }
  }

  fun refresh() {
    if (host.trim().isEmpty()) {
      Toast.makeText(context, "Укажите адрес сервера", Toast.LENGTH_SHORT).show()
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

  Scaffold(
    topBar = {
      Column {
        TopAppBar(
          title = {
            Column {
              Text("Super Resolution", fontWeight = FontWeight.Bold)
              Text(
                "Monitor",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.88f),
              )
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SrBlueDeep,
            titleContentColor = Color.White,
          ),
        )
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(SrOrange),
        )
        TabRow(selectedTabIndex = selectedTab, containerColor = SrBlueDeep, contentColor = Color.White) {
          Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Монитор") })
          Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Файлы") })
        }
      }
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      when (selectedTab) {
        0 -> Column(
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
            updatedAt = uiState.updatedAt,
          )

          SettingsCard(
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
            isManualRefreshing = isManualRefreshing,
            onRefresh = { refresh() },
          )

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = "© @MaxCherepanov",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        1 -> FilesTab(config = connectionConfig.copy(host = host.trim()), isVisible = selectedTab == 1)
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
          trackColor = SrOutlineVariant,
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
          trackColor = SrOutlineVariant,
        )
      } else {
        Text(
          text = if (isOnline == true) "Нет активной обработки" else "—",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("В очереди: ${queueSize?.toString() ?: "—"}")
        Text("Готово сегодня: ${doneToday?.toString() ?: "—"}")
      }

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
    focusedBorderColor = SrBlueDeep,
    focusedLabelColor = SrBlueDeep,
    cursorColor = SrBlueDeep,
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
