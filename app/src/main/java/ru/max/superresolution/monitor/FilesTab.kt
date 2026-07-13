package ru.max.superresolution.monitor

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val AUTO_REFRESH_MS = 5_000L

@Composable
fun FilesTab(config: ConnectionConfig, isVisible: Boolean) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var inputFiles by remember { mutableStateOf<List<InputFile>>(emptyList()) }
  var readyJobs by remember { mutableStateOf<List<ReadyJob>>(emptyList()) }
  var isLoading by remember { mutableStateOf(false) }
  var busyAction by remember { mutableStateOf<String?>(null) }
  var transferProgress by remember { mutableStateOf<TransferProgress?>(null) }

  fun connectionConfig() = config.copy(host = config.host.trim())

  fun reload(silent: Boolean = false) {
    if (config.host.isBlank() || busyAction != null) return
    scope.launch {
      if (!silent) isLoading = true
      try {
        val api = NetworkClient.createApi(config.host, config.port, config.username, config.password)
        val files = withContext(Dispatchers.IO) { api.listInputFiles() }
        val ready = withContext(Dispatchers.IO) { api.listReadyJobs() }
        inputFiles = files.files
        readyJobs = ready.jobs
      } catch (e: Exception) {
        if (!silent) {
          Toast.makeText(context, e.message ?: "Ошибка загрузки списка", Toast.LENGTH_LONG).show()
        }
      } finally {
        if (!silent) isLoading = false
      }
    }
  }

  val pickFile = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      busyAction = "upload"
      transferProgress = null
      try {
        val result = withContext(Dispatchers.IO) {
          FileTransfer.upload(context, connectionConfig(), uri) { progress ->
            transferProgress = progress
          }
        }
        val msg = buildString {
          if (result.savedAudio > 0) append("Загружено: ${result.savedAudio}")
          if (result.errors.isNotEmpty()) {
            if (isNotEmpty()) append("; ")
            append(result.errors.joinToString("; "))
          }
        }
        Toast.makeText(context, msg.ifEmpty { "Готово" }, Toast.LENGTH_LONG).show()
        reload(silent = true)
      } catch (e: Exception) {
        Toast.makeText(context, e.message ?: "Ошибка загрузки", Toast.LENGTH_LONG).show()
      } finally {
        busyAction = null
        transferProgress = null
      }
    }
  }

  LaunchedEffect(config.host, config.port, config.username, config.password) {
    if (config.host.isNotBlank()) reload()
  }

  LaunchedEffect(isVisible, config.host, busyAction) {
    if (!isVisible || config.host.isBlank()) return@LaunchedEffect
    while (isActive) {
      delay(AUTO_REFRESH_MS)
      if (busyAction == null) reload(silent = true)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (config.host.isBlank()) {
      Text(
        text = "Сначала укажите адрес сервера на вкладке «Монитор»",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      return@Column
    }

    transferProgress?.let { progress ->
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(progress.label, style = MaterialTheme.typography.bodyMedium)
          if (progress.fraction != null) {
            LinearProgressIndicator(
              progress = { progress.fraction },
              modifier = Modifier.fillMaxWidth().height(8.dp),
              color = SrOrange,
              trackColor = SrOutlineVariant,
            )
            Text("${(progress.fraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
          } else {
            LinearProgressIndicator(
              modifier = Modifier.fillMaxWidth().height(8.dp),
              color = SrOrange,
              trackColor = SrOutlineVariant,
            )
          }
        }
      }
    }

    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("На сервере (input)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          if (isLoading) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        }

        Button(
          onClick = { pickFile.launch(arrayOf("audio/*")) },
          enabled = busyAction == null,
          modifier = Modifier.fillMaxWidth(),
          colors = ButtonDefaults.buttonColors(containerColor = SrBlueDeep, contentColor = Color.White),
        ) {
          Text(if (busyAction == "upload") "Загрузка…" else "Загрузить файл на сервер")
        }

        if (inputFiles.isEmpty() && !isLoading) {
          Text("Нет файлов", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        inputFiles.forEach { file ->
          InputFileRow(
            file = file,
            busy = busyAction != null,
            onProcess = {
              scope.launch {
                busyAction = "process:${file.name}"
                try {
                  val api = NetworkClient.createApi(config.host, config.port, config.username, config.password)
                  val result = withContext(Dispatchers.IO) {
                    api.process(ProcessRequest(filenames = listOf(file.name), preset = "ai_flac"))
                  }
                  Toast.makeText(context, processMessage(result), Toast.LENGTH_LONG).show()
                  reload(silent = true)
                } catch (e: Exception) {
                  Toast.makeText(context, e.message ?: "Ошибка", Toast.LENGTH_LONG).show()
                } finally {
                  busyAction = null
                }
              }
            },
            onDelete = {
              scope.launch {
                busyAction = "delete:${file.name}"
                try {
                  val api = NetworkClient.createApi(config.host, config.port, config.username, config.password)
                  withContext(Dispatchers.IO) { api.deleteInputFile(file.name) }
                  Toast.makeText(context, "Удалено: ${file.name}", Toast.LENGTH_SHORT).show()
                  reload(silent = true)
                } catch (e: Exception) {
                  Toast.makeText(context, e.message ?: "Ошибка удаления", Toast.LENGTH_LONG).show()
                } finally {
                  busyAction = null
                }
              }
            },
          )
        }

        OutlinedButton(onClick = { reload() }, enabled = busyAction == null && !isLoading, modifier = Modifier.fillMaxWidth()) {
          Text("Обновить список")
        }
      }
    }

    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Готово к скачиванию", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        if (readyJobs.isEmpty() && !isLoading) {
          Text("Нет готовых файлов", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        readyJobs.forEach { job ->
          ReadyJobRow(
            job = job,
            busy = busyAction != null,
            onDownload = {
              scope.launch {
                busyAction = "download:${job.id}"
                transferProgress = null
                try {
                  val path = withContext(Dispatchers.IO) {
                    FileTransfer.download(context, connectionConfig(), job.id, job.downloadFilename) { progress ->
                      transferProgress = progress
                    }
                  }
                  Toast.makeText(context, "Сохранено: $path", Toast.LENGTH_LONG).show()
                  reload(silent = true)
                } catch (e: Exception) {
                  Toast.makeText(context, e.message ?: "Ошибка скачивания", Toast.LENGTH_LONG).show()
                } finally {
                  busyAction = null
                  transferProgress = null
                }
              }
            },
          )
        }
      }
    }
  }
}

@Composable
private fun InputFileRow(
  file: InputFile,
  busy: Boolean,
  onProcess: () -> Unit,
  onDelete: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(file.name, fontWeight = FontWeight.Medium)
    Text(
      "${formatFileSize(file.sizeBytes)}${if (file.busy) " · в очереди" else ""}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(
        onClick = onProcess,
        enabled = !busy && !file.busy,
        modifier = Modifier.weight(1f),
      ) {
        Text("AI → FLAC")
      }
      TextButton(
        onClick = onDelete,
        enabled = !busy && !file.busy,
      ) {
        Text("Удалить", color = SrErrorRed)
      }
    }
  }
}

@Composable
private fun ReadyJobRow(job: ReadyJob, busy: Boolean, onDownload: () -> Unit) {
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(job.downloadFilename, fontWeight = FontWeight.Medium)
    job.optionsSummary?.let {
      Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    OutlinedButton(onClick = onDownload, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
      Text(if (busy) "Скачивание…" else "Скачать")
    }
  }
}

private fun processMessage(result: ProcessResponse): String {
  return when {
    result.queued > 0 -> "В очередь: ${result.queued}"
    result.skippedBusy.isNotEmpty() -> "Уже в очереди: ${result.skippedBusy.joinToString()}"
    result.skippedNoop.isNotEmpty() -> "Без изменений: ${result.skippedNoop.joinToString()}"
    result.skippedMissing.isNotEmpty() -> "Не найдено: ${result.skippedMissing.joinToString()}"
    else -> "Не добавлено в очередь"
  }
}
