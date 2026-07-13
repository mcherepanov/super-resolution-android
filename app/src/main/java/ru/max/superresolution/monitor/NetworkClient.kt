package ru.max.superresolution.monitor

import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
  private const val CONNECT_TIMEOUT_SEC = 15L
  private const val READ_TIMEOUT_SEC = 15L
  private const val TRANSFER_TIMEOUT_SEC = 300L

  fun createApi(host: String, port: String, username: String?, password: String?): ApiService {
    return buildRetrofit(host, port, username, password, READ_TIMEOUT_SEC).create(ApiService::class.java)
  }

  fun createTransferApi(host: String, port: String, username: String?, password: String?): ApiService {
    return buildRetrofit(host, port, username, password, TRANSFER_TIMEOUT_SEC).create(ApiService::class.java)
  }

  private fun buildRetrofit(
    host: String,
    port: String,
    username: String?,
    password: String?,
    readTimeoutSec: Long,
  ): Retrofit {
    val baseUrl = "http://${host.trim()}:${port.trim().ifEmpty { "8080" }}/"

    val logging = HttpLoggingInterceptor().apply {
      level = HttpLoggingInterceptor.Level.BASIC
    }

    val clientBuilder = OkHttpClient.Builder()
      .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
      .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
      .writeTimeout(readTimeoutSec, TimeUnit.SECONDS)
      .addInterceptor(logging)

    val user = username?.trim().orEmpty()
    val pass = password?.trim().orEmpty()
    if (user.isNotEmpty() && pass.isNotEmpty()) {
      clientBuilder.addInterceptor { chain ->
        val request = chain.request().newBuilder()
          .header("Authorization", Credentials.basic(user, pass))
          .build()
        chain.proceed(request)
      }
    }

    return Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(clientBuilder.build())
      .addConverterFactory(GsonConverterFactory.create())
      .build()
  }
}
