package ru.max.superresolution.monitor

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

interface ApiService {
    @GET("api/mobile-status")
    suspend fun getMobileStatus(): StatusResponse

    @GET("api/input/files")
    suspend fun listInputFiles(): InputFilesResponse

    @DELETE("api/input/files/{filename}")
    suspend fun deleteInputFile(@Path("filename", encoded = true) filename: String): DeleteInputResponse

    @Multipart
    @POST("api/input/upload")
    suspend fun uploadFiles(@Part files: List<MultipartBody.Part>): UploadResponse

    @POST("api/process")
    suspend fun process(@Body body: ProcessRequest): ProcessResponse

    @GET("api/jobs/ready")
    suspend fun listReadyJobs(): ReadyJobsResponse

    @Streaming
    @GET("api/jobs/{id}/download")
    suspend fun downloadJob(@Path("id") jobId: Int): ResponseBody
}
