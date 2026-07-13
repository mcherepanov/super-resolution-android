package ru.max.superresolution.monitor

import retrofit2.http.GET

interface ApiService {
    @GET("api/mobile-status")
    suspend fun getMobileStatus(): StatusResponse
}
