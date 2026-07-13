package ru.max.superresolution.monitor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import java.io.File
import java.io.InputStream

data class TransferProgress(
  val label: String,
  val fraction: Float?,
)

object FileTransfer {
  suspend fun upload(
    context: Context,
    config: ConnectionConfig,
    uri: Uri,
    onProgress: (TransferProgress) -> Unit,
  ): UploadResponse {
    val name = resolveDisplayName(context, uri) ?: "upload.bin"
    val size = resolveSize(context, uri)
    val input = context.contentResolver.openInputStream(uri)
      ?: throw IllegalStateException("Не удалось прочитать файл")
    onProgress(TransferProgress("Загрузка: $name", 0f))
    val api = NetworkClient.createTransferApi(config.host, config.port, config.username, config.password)
    val body = ProgressRequestBody(
      mediaType = "application/octet-stream".toMediaType(),
      input = input,
      length = size,
      onProgress = { fraction ->
        onProgress(TransferProgress("Загрузка: $name", fraction))
      },
    )
    val part = MultipartBody.Part.createFormData("files", name, body)
    return api.uploadFiles(listOf(part))
  }

  suspend fun download(
    context: Context,
    config: ConnectionConfig,
    jobId: Int,
    filename: String,
    onProgress: (TransferProgress) -> Unit,
  ): String {
    onProgress(TransferProgress("Скачивание: $filename", 0f))
    val api = NetworkClient.createTransferApi(config.host, config.port, config.username, config.password)
    api.downloadJob(jobId).use { body ->
      val total = body.contentLength()
      body.byteStream().use { input ->
        return saveToDownloads(context, filename, input, total, onProgress)
      }
    }
  }

  private fun resolveDisplayName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) {
          return cursor.getString(idx)
        }
      }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
  }

  private fun resolveSize(context: Context, uri: Uri): Long {
    if (uri.scheme == "content") {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (idx >= 0 && cursor.moveToFirst()) {
          val size = cursor.getLong(idx)
          if (size > 0) return size
        }
      }
    }
    return -1L
  }

  private fun saveToDownloads(
    context: Context,
    filename: String,
    input: InputStream,
    totalBytes: Long,
    onProgress: (TransferProgress) -> Unit,
  ): String {
    val mime = guessMime(filename)
    val label = "Скачивание: $filename"

    fun report(written: Long) {
      val fraction = if (totalBytes > 0) {
        (written.toFloat() / totalBytes).coerceIn(0f, 1f)
      } else {
        null
      }
      onProgress(TransferProgress(label, fraction))
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, filename)
        put(MediaStore.Downloads.MIME_TYPE, mime)
        put(MediaStore.Downloads.IS_PENDING, 1)
      }
      val resolver = context.contentResolver
      val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: throw IllegalStateException("Не удалось создать файл в Загрузках")
      var written = 0L
      resolver.openOutputStream(uri)?.use { out ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
          val read = input.read(buffer)
          if (read == -1) break
          out.write(buffer, 0, read)
          written += read
          report(written)
        }
      } ?: throw IllegalStateException("Не удалось записать файл")
      values.clear()
      values.put(MediaStore.Downloads.IS_PENDING, 0)
      resolver.update(uri, values, null, null)
      onProgress(TransferProgress(label, 1f))
      return filename
    }

    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, filename)
    var written = 0L
    file.outputStream().use { out ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        out.write(buffer, 0, read)
        written += read
        report(written)
      }
    }
    onProgress(TransferProgress(label, 1f))
    return file.absolutePath
  }

  private fun guessMime(filename: String): String {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
  }

  private const val DEFAULT_BUFFER_SIZE = 8192
}

fun formatFileSize(bytes: Long): String {
  if (bytes < 1024) return "$bytes B"
  val kb = bytes / 1024.0
  if (kb < 1024) return String.format("%.1f KB", kb)
  val mb = kb / 1024.0
  return String.format("%.1f MB", mb)
}
