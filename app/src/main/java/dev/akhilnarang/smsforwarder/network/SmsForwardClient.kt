package dev.akhilnarang.smsforwarder.network

import dev.akhilnarang.smsforwarder.data.ForwardRecordEntity
import dev.akhilnarang.smsforwarder.settings.AppSettings
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
    @Volatile
    private var cachedClient: OkHttpClient? = null

    @Volatile
    private var cachedConnectTimeout: Int = -1

    @Volatile
    private var cachedReadTimeout: Int = -1

    override suspend fun forward(
        record: ForwardRecordEntity,
        settings: AppSettings,
    ): ForwardResult =
        withContext(Dispatchers.IO) {
            val endpoint = settings.endpointUrl.trim().toHttpUrlOrNull()
                ?: return@withContext ForwardResult.PermanentFailure("Endpoint URL is not configured or is invalid.")
            if (endpoint.scheme != HTTPS_SCHEME) {
                return@withContext ForwardResult.PermanentFailure("Endpoint URL must use HTTPS.")
            }

            if (settings.authHeaderName.isNotBlank() || settings.authHeaderValue.isNotBlank()) {
                if (settings.authHeaderName.isBlank() || settings.authHeaderValue.isBlank()) {
                    return@withContext ForwardResult.PermanentFailure("Auth header is incomplete.")
                }
                if (!isValidHeaderName(settings.authHeaderName)) {
                    return@withContext ForwardResult.PermanentFailure("Auth header name contains unsupported characters.")
                }
                if (!isValidHeaderValue(settings.authHeaderValue)) {
                    return@withContext ForwardResult.PermanentFailure("Auth header value contains unsupported characters.")
                }
            }

            val request =
                try {
                    Request.Builder()
                        .url(endpoint)
                        .post(record.payloadJson.toRequestBody(JSON_MEDIA_TYPE))
                        .apply {
                            if (settings.authHeaderName.isNotBlank()) {
                                header(settings.authHeaderName.trim(), settings.authHeaderValue)
                            }
                        }
                        .build()
                } catch (_: IllegalArgumentException) {
                    return@withContext ForwardResult.PermanentFailure(
                        "Auth header contains unsupported characters.",
                    )
                }

            val client =
                getClient(
                    connectTimeout = settings.connectTimeoutSeconds.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS),
                    readTimeout = settings.readTimeoutSeconds.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS),
                )

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

    private fun getClient(connectTimeout: Int, readTimeout: Int): OkHttpClient {
        val currentClient = cachedClient
        if (
            currentClient != null &&
            cachedConnectTimeout == connectTimeout &&
            cachedReadTimeout == readTimeout
        ) {
            return currentClient
        }

        return OkHttpClient.Builder()
            .connectTimeout(connectTimeout.toLong(), TimeUnit.SECONDS)
            .writeTimeout(readTimeout.toLong(), TimeUnit.SECONDS)
            .readTimeout(readTimeout.toLong(), TimeUnit.SECONDS)
            .build()
            .also {
                cachedClient = it
                cachedConnectTimeout = connectTimeout
                cachedReadTimeout = readTimeout
            }
    }

    private companion object {
        const val HTTPS_SCHEME = "https"
        const val MAX_ERROR_RESPONSE_CHARS = 500
        const val MIN_TIMEOUT_SECONDS = 1
        const val MAX_TIMEOUT_SECONDS = 120
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

sealed interface ForwardResult {
    data class Success(val responseDetails: String?) : ForwardResult
    data class RetryableFailure(val message: String) : ForwardResult
    data class PermanentFailure(val message: String) : ForwardResult
}
