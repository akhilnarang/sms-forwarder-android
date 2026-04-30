package dev.akhilnarang.smsforwarder.settings

data class AppSettings(
    val endpointUrl: String = "",
    val authHeaderName: String = "",
    val authHeaderValue: String = "",
    val connectTimeoutSeconds: Int = 15,
    val readTimeoutSeconds: Int = 15,
)
