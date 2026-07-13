package ru.max.superresolution.monitor

import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
  private const val CONNECT_TIMEOUT_SEC = 10L
  private const val READ_TIMEOUT_SEC = 15L

  fun createApi(host: String, port: String, username: String?, password: String?): ApiService {
    val baseUrl = "http://${host.trim()}:${port.trim().ifEmpty { "8080" }}/"

    val logging = HttpLoggingInterceptor().apply {
      level = HttpLoggingInterceptor.Level.BASIC
    }

    val clientBuilder = OkHttpClient.Builder()
      .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
      .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
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
      .create(ApiService::class.java)
  }
}
