package dev.akhilnarang.smsforwarder.network

import dev.akhilnarang.smsforwarder.data.ForwardRecordEntity
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SmsForwardClient : ForwardClientInterface {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_READ_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .build()

    override suspend fun forward(
        record: ForwardRecordEntity,
        endpointUrl: String,
        authHeaderName: String,
        authHeaderValue: String,
    ): ForwardResult =
        withContext(Dispatchers.IO) {
            val endpoint = endpointUrl.trim().toHttpUrlOrNull()
                ?: return@withContext ForwardResult.PermanentFailure("Endpoint URL is not configured or is invalid.")
            if (endpoint.scheme != HTTPS_SCHEME) {
                return@withContext ForwardResult.PermanentFailure("Endpoint URL must use HTTPS.")
            }

            if (authHeaderName.isNotBlank() || authHeaderValue.isNotBlank()) {
                if (authHeaderName.isBlank() || authHeaderValue.isBlank()) {
                    return@withContext ForwardResult.PermanentFailure("Auth header is incomplete.")
                }
                if (!isValidHeaderName(authHeaderName)) {
                    return@withContext ForwardResult.PermanentFailure("Auth header name contains unsupported characters.")
                }
                if (!isValidHeaderValue(authHeaderValue)) {
                    return@withContext ForwardResult.PermanentFailure("Auth header value contains unsupported characters.")
                }
            }

            val request =
                try {
                    Request.Builder()
                        .url(endpoint)
                        .post(record.payloadJson.toRequestBody(JSON_MEDIA_TYPE))
                        .apply {
                            if (authHeaderName.isNotBlank()) {
                                header(authHeaderName.trim(), authHeaderValue)
                            }
                        }
                        .build()
                } catch (_: IllegalArgumentException) {
                    return@withContext ForwardResult.PermanentFailure(
                        "Auth header contains unsupported characters.",
                    )
                }

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()?.trim()?.take(MAX_ERROR_RESPONSE_CHARS).orEmpty()
                        val details = if (responseBody.isNotEmpty()) "Server returned ${response.code}: $responseBody" else "Server returned ${response.code}"
                        return@withContext ForwardResult.Success(details)
                    }

                    val responseSummary =
                        response.body?.string()?.trim()?.take(MAX_ERROR_RESPONSE_CHARS).orEmpty()
                            .ifBlank { response.message }
                    return@withContext if (response.code >= 500 || response.code == 429) {
                        ForwardResult.RetryableFailure("Server returned ${response.code}: $responseSummary")
                    } else {
                        ForwardResult.PermanentFailure("Server returned ${response.code}: $responseSummary")
                    }
                }
            } catch (error: UnknownHostException) {
                return@withContext ForwardResult.RetryableFailure(error.message ?: "Unable to resolve server host.")
            } catch (error: SocketTimeoutException) {
                return@withContext ForwardResult.RetryableFailure(error.message ?: "Server request timed out.")
            } catch (error: IOException) {
                return@withContext ForwardResult.RetryableFailure(error.message ?: "Network request failed.")
            }
        }

    private companion object {
        const val HTTPS_SCHEME = "https"
        const val MAX_ERROR_RESPONSE_CHARS = 500
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 15
        const val DEFAULT_READ_TIMEOUT_SECONDS = 15
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

sealed interface ForwardResult {
    data class Success(val responseDetails: String?) : ForwardResult
    data class RetryableFailure(val message: String) : ForwardResult
    data class PermanentFailure(val message: String) : ForwardResult
}
