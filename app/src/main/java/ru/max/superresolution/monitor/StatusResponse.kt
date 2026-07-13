
package ru.max.superresolution.monitor

import com.google.gson.annotations.SerializedName

data class CurrentJob(
    @SerializedName("id") val id: Int,
    @SerializedName("filename") val filename: String,
    @SerializedName("input_format") val inputFormat: String? = null,
    @SerializedName("job_type") val jobType: String? = null,
    @SerializedName("progress_pct") val progressPct: Double? = null,
    @SerializedName("progress_detail") val progressDetail: String? = null,
    @SerializedName("output_format") val outputFormat: String? = null,
    @SerializedName("options_summary") val optionsSummary: String? = null,
)

data class StatusResponse(
    @SerializedName("status") val status: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("app_version") val appVersion: String? = null,
    @SerializedName("app_build") val appBuild: Int? = null,
    @SerializedName("queue_size") val queueSize: Int,
    @SerializedName("workers_total") val workersTotal: Int,
    @SerializedName("workers_busy") val workersBusy: Int,
    @SerializedName("tasks_completed_today") val tasksCompletedToday: Int? = null,
    @SerializedName("current_job") val currentJob: CurrentJob? = null,
    @SerializedName("error_message") val errorMessage: String? = null,
)
