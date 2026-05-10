package dev.akhilnarang.smsforwarder.data

import kotlinx.serialization.Serializable

const val BACKUP_SCHEMA_VERSION = 1

@Serializable
data class BackupFile(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val exportedAtEpochMs: Long,
    val appVersionName: String? = null,
    val destinations: List<BackupDestination>,
    val rules: List<BackupRule>,
)

@Serializable
data class BackupDestination(
    val label: String,
    val type: String,
    val endpointUrl: String,
    val authHeaderName: String? = null,
    val authHeaderValue: String? = null,
    val payloadTemplate: String? = null,
    val configJson: String? = null,
    val enabled: Boolean = true,
)

@Serializable
data class BackupRule(
    val priority: Int,
    val label: String,
    val senderPattern: String,
    val bodyContains: String? = null,
    val destinationIndex: Int,
    val customPayloadKeys: String? = null,
    val enabled: Boolean = true,
)

enum class ImportMode {
    REPLACE,
    MERGE,
}

data class ImportResult(
    val destinationsImported: Int,
    val rulesImported: Int,
    val rulesSkipped: Int,
)
