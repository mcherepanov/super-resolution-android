package ru.max.superresolution.monitor

import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ApiErrors {
  fun userMessage(error: Throwable): String {
    var current: Throwable? = error
    while (current != null) {
      when (current) {
        is HttpException -> return httpMessage(current.code())
        is SocketTimeoutException -> return "Сервер не ответил вовремя (timeout)"
        is UnknownHostException -> return "Сервер не найден. Проверьте IP и сеть"
        is ConnectException -> return "Нет связи с сервером. Проверьте Wi‑Fi / VPN"
        is IOException -> {
          val msg = current.message?.lowercase().orEmpty()
          when {
            "timeout" in msg -> return "Сервер не ответил вовремя (timeout)"
            "failed to connect" in msg || "connection refused" in msg ||
              "unable to resolve" in msg || "network is unreachable" in msg ->
              return "Нет связи с сервером. Проверьте Wi‑Fi / VPN"
            "401" in msg || "unauthorized" in msg ->
              return "Неверный логин или пароль"
          }
        }
      }
      current = current.cause
    }
    return "Не удалось выполнить запрос"
  }

  private fun httpMessage(code: Int): String = when (code) {
    401, 403 -> "Неверный логин или пароль"
    404 -> "Сервер не нашёл запрошенный ресурс"
    in 500..599 -> "Сервер временно недоступен ($code)"
    else -> "Ошибка HTTP $code"
  }
}
