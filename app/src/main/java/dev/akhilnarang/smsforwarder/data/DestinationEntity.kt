package dev.akhilnarang.smsforwarder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DestinationType {
    CUSTOM_WEBHOOK,
    TELEGRAM_PRESET
}

@Entity(tableName = "destinations")
data class DestinationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val type: DestinationType,
    val endpointUrl: String,
    val authHeaderName: String? = null,
    val authHeaderValue: String? = null,
    val payloadTemplate: String? = null,
    val configJson: String? = null,
    val enabled: Boolean = true
)
