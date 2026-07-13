package ru.max.superresolution.monitor

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.InputStream

class ProgressRequestBody(
  private val mediaType: MediaType,
  private val input: InputStream,
  private val length: Long,
  private val onProgress: (Float) -> Unit,
) : RequestBody() {

  override fun contentType(): MediaType = mediaType

  override fun contentLength(): Long = length

  override fun writeTo(sink: BufferedSink) {
    try {
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      var uploaded = 0L
      while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        sink.write(buffer, 0, read)
        uploaded += read
        if (length > 0) {
          onProgress((uploaded.toFloat() / length).coerceIn(0f, 1f))
        }
      }
      onProgress(1f)
    } finally {
      input.close()
    }
  }

  private companion object {
    const val DEFAULT_BUFFER_SIZE = 8192
  }
}
