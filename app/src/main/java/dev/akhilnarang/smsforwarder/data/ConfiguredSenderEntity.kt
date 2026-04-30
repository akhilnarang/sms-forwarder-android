package dev.akhilnarang.smsforwarder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "configured_senders",
    indices = [Index(value = ["normalizedSender"], unique = true)],
)
data class ConfiguredSenderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val rawSender: String,
    val normalizedSender: String,
    val enabled: Boolean = true,
)

