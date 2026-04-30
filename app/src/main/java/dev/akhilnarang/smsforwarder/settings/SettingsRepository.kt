package dev.akhilnarang.smsforwarder.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.akhilnarang.smsforwarder.network.isValidHeaderName
import dev.akhilnarang.smsforwarder.network.isValidHeaderValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class SettingsRepository(
    context: Context,
) : SettingsGateway {
    private val sharedPreferences: SharedPreferences
    private val settingsState: MutableStateFlow<AppSettings>

    init {
        val masterKey =
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

        sharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                PREFERENCES_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        settingsState = MutableStateFlow(readSettings())
        sharedPreferences.registerOnSharedPreferenceChangeListener { _, _ ->
            settingsState.value = readSettings()
        }
    }

    fun observeSettings(): StateFlow<AppSettings> = settingsState

    override fun currentSettings(): AppSettings = settingsState.value

    fun saveSettings(
        endpointUrl: String,
        authHeaderName: String,
        authHeaderValue: String,
        connectTimeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
        readTimeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    ) {
        val normalizedEndpoint = endpointUrl.trim()
        val normalizedHeaderName = authHeaderName.trim()
        val normalizedHeaderValue = authHeaderValue.trim()

        if (normalizedEndpoint.isNotBlank()) {
            val parsedEndpoint = normalizedEndpoint.toHttpUrlOrNull()
            require(parsedEndpoint != null && parsedEndpoint.scheme == HTTPS_SCHEME) {
                "Endpoint URL must be a valid HTTPS URL."
            }
        }

        require(connectTimeoutSeconds in MIN_TIMEOUT_SECONDS..MAX_TIMEOUT_SECONDS) {
            "Connect timeout must be between $MIN_TIMEOUT_SECONDS and $MAX_TIMEOUT_SECONDS seconds."
        }
        require(readTimeoutSeconds in MIN_TIMEOUT_SECONDS..MAX_TIMEOUT_SECONDS) {
            "Read timeout must be between $MIN_TIMEOUT_SECONDS and $MAX_TIMEOUT_SECONDS seconds."
        }

        require(
            (normalizedHeaderName.isBlank() && normalizedHeaderValue.isBlank()) ||
                (normalizedHeaderName.isNotBlank() && normalizedHeaderValue.isNotBlank()),
        ) {
            "Auth header name and value must both be filled or both be blank."
        }
        if (normalizedHeaderName.isNotBlank()) {
            require(isValidHeaderName(normalizedHeaderName)) {
                "Auth header name contains unsupported characters."
            }
            require(isValidHeaderValue(normalizedHeaderValue)) {
                "Auth header value must use printable ASCII without newlines."
            }
        }

        sharedPreferences
            .edit()
            .putString(KEY_ENDPOINT_URL, normalizedEndpoint)
            .putString(KEY_AUTH_HEADER_NAME, normalizedHeaderName)
            .putString(KEY_AUTH_HEADER_VALUE, normalizedHeaderValue)
            .putInt(KEY_CONNECT_TIMEOUT, connectTimeoutSeconds)
            .putInt(KEY_READ_TIMEOUT, readTimeoutSeconds)
            .apply()
    }

    private fun readSettings(): AppSettings =
        AppSettings(
            endpointUrl = sharedPreferences.getString(KEY_ENDPOINT_URL, "").orEmpty(),
            authHeaderName = sharedPreferences.getString(KEY_AUTH_HEADER_NAME, "").orEmpty(),
            authHeaderValue = sharedPreferences.getString(KEY_AUTH_HEADER_VALUE, "").orEmpty(),
            connectTimeoutSeconds = sharedPreferences.getInt(KEY_CONNECT_TIMEOUT, DEFAULT_TIMEOUT_SECONDS),
            readTimeoutSeconds = sharedPreferences.getInt(KEY_READ_TIMEOUT, DEFAULT_TIMEOUT_SECONDS),
        )

    private companion object {
        const val HTTPS_SCHEME = "https"
        const val PREFERENCES_NAME = "sms_forwarder_settings"
        const val KEY_ENDPOINT_URL = "endpoint_url"
        const val KEY_AUTH_HEADER_NAME = "auth_header_name"
        const val KEY_AUTH_HEADER_VALUE = "auth_header_value"
        const val KEY_CONNECT_TIMEOUT = "connect_timeout"
        const val KEY_READ_TIMEOUT = "read_timeout"
        const val DEFAULT_TIMEOUT_SECONDS = 15
        const val MIN_TIMEOUT_SECONDS = 1
        const val MAX_TIMEOUT_SECONDS = 120
    }
}
