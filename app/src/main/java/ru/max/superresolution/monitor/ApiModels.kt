package ru.max.superresolution.monitor

import com.google.gson.annotations.SerializedName

data class InputFile(
    @SerializedName("name") val name: String,
    @SerializedName("size_bytes") val sizeBytes: Long,
    @SerializedName("busy") val busy: Boolean,
    @SerializedName("corrupted") val corrupted: Boolean = false,
    @SerializedName("error_message") val errorMessage: String? = null,
)

data class InputFilesResponse(
    @SerializedName("status") val status: String,
    @SerializedName("files") val files: List<InputFile>,
)

data class UploadResponse(
    @SerializedName("status") val status: String,
    @SerializedName("saved_audio") val savedAudio: Int,
    @SerializedName("saved_names") val savedNames: List<String>,
    @SerializedName("corrupted_names") val corruptedNames: List<String> = emptyList(),
    @SerializedName("errors") val errors: List<String>,
)

data class ProcessRequest(
    @SerializedName("filenames") val filenames: List<String>,
    @SerializedName("preset") val preset: String? = null,
)

data class ProcessResponse(
    @SerializedName("status") val status: String,
    @SerializedName("queued") val queued: Int,
    @SerializedName("job_ids") val jobIds: List<Int>,
    @SerializedName("skipped_missing") val skippedMissing: List<String>,
    @SerializedName("skipped_noop") val skippedNoop: List<String>,
    @SerializedName("skipped_busy") val skippedBusy: List<String>,
    @SerializedName("skipped_corrupted") val skippedCorrupted: List<String> = emptyList(),
)

data class ReadyJob(
    @SerializedName("id") val id: Int,
    @SerializedName("filename") val filename: String,
    @SerializedName("output_format") val outputFormat: String?,
    @SerializedName("download_filename") val downloadFilename: String,
    @SerializedName("finished_at") val finishedAt: String?,
    @SerializedName("options_summary") val optionsSummary: String?,
)

data class ReadyJobsResponse(
    @SerializedName("status") val status: String,
    @SerializedName("jobs") val jobs: List<ReadyJob>,
)

data class DeleteInputResponse(
    @SerializedName("status") val status: String,
    @SerializedName("deleted") val deleted: String,
)
