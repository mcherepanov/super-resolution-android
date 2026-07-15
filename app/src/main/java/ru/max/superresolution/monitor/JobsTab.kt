package ru.max.superresolution.monitor

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val AUTO_REFRESH_MS = 5_000L

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun JobsTab(config: ConnectionConfig, isVisible: Boolean) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var jobs by remember { mutableStateOf<List<JobItem>>(emptyList()) }
  var isLoading by remember { mutableStateOf(false) }
  var isRefreshing by remember { mutableStateOf(false) }
  var busyCancelId by remember { mutableStateOf<Int?>(null) }
  var lastError by remember { mutableStateOf<String?>(null) }

  fun reload(silent: Boolean = false) {
    if (config.host.isBlank() || busyCancelId != null) return
    scope.launch {
      if (!silent) isLoading = true
      isRefreshing = true
      try {
        val api = NetworkClient.createApi(config.host, config.port, config.username, config.password)
        val result = withContext(Dispatchers.IO) { api.listJobs() }
        jobs = result.jobs
        lastError = null
      } catch (e: Exception) {
        lastError = ApiErrors.userMessage(e)
        if (!silent) {
          Toast.makeText(context, lastError, Toast.LENGTH_LONG).show()
        }
      } finally {
        isLoading = false
        isRefreshing = false
      }
    }
  }

  fun cancelJob(job: JobItem) {
    if (config.host.isBlank() || !job.canCancel) return
    scope.launch {
      busyCancelId = job.id
      try {
        val api = NetworkClient.createApi(config.host, config.port, config.username, config.password)
        val result = withContext(Dispatchers.IO) { api.cancelJob(job.id) }
        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
        reload(silent = true)
      } catch (e: Exception) {
        Toast.makeText(context, ApiErrors.userMessage(e), Toast.LENGTH_LONG).show()
      } finally {
        busyCancelId = null
      }
    }
  }

  LaunchedEffect(config.host, config.port, config.username, config.password) {
    if (config.host.isNotBlank()) reload()
  }

  LaunchedEffect(isVisible, config.host, busyCancelId) {
    if (!isVisible || config.host.isBlank()) return@LaunchedEffect
    while (isActive) {
      delay(AUTO_REFRESH_MS)
      if (busyCancelId == null) reload(silent = true)
    }
  }

  if (config.host.isBlank()) {
    Text(
      text = "Сначала укажите адрес сервера в «Настройки»",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }

  val pullState = rememberPullRefreshState(
    refreshing = isRefreshing,
    onRefresh = { reload() },
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
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("История задач", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (isLoading) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
      }

      lastError?.let {
        Text(it, color = SrErrorRed, style = MaterialTheme.typography.bodyMedium)
      }

      if (jobs.isEmpty() && !isLoading) {
        Text(
          "Нет задач",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      jobs.forEach { job ->
        JobCard(
          job = job,
          cancelling = busyCancelId == job.id,
          busy = busyCancelId != null,
          onCancel = { cancelJob(job) },
        )
      }
    }

    PullRefreshIndicator(
      refreshing = isRefreshing,
      state = pullState,
      modifier = Modifier.align(Alignment.TopCenter),
      contentColor = SrOrange,
    )
  }
}

@Composable
private fun JobCard(
  job: JobItem,
  cancelling: Boolean,
  busy: Boolean,
  onCancel: () -> Unit,
) {
  val statusColor = when (job.status) {
    "done" -> SrDoneGreen
    "failed", "corrupted" -> SrErrorRed
    "processing", "queued" -> SrOrange
    "cancelled" -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("#${job.id}", fontWeight = FontWeight.SemiBold)
        Text(
          job.status.uppercase(),
          color = statusColor,
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.labelLarge,
        )
      }
      Text(job.filename, fontWeight = FontWeight.Medium)
      job.optionsSummary?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      job.errorMessage?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = SrErrorRed)
      }
      Text(
        formatJobTimes(job),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (job.canCancel) {
        OutlinedButton(
          onClick = onCancel,
          enabled = !busy,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            when {
              cancelling -> "Прерывание…"
              job.cancelRequested -> "Ожидание прерывания…"
              job.status == "queued" -> "Снять с очереди"
              else -> "Прервать"
            },
          )
        }
      }
    }
  }
}

private fun formatJobTimes(job: JobItem): String {
  val parts = mutableListOf<String>()
  job.createdAt?.let { parts += "создано ${formatIsoShort(it)}" }
  job.finishedAt?.let { parts += "завершено ${formatIsoShort(it)}" }
  job.durationSec?.let { parts += "%.1fs".format(it) }
  return parts.joinToString(" · ").ifEmpty { "—" }
}

private fun formatIsoShort(iso: String): String {
  return iso.replace("T", " ").take(19)
}
