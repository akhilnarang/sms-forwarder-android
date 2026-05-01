package dev.akhilnarang.smsforwarder.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "forwarding_rules",
    foreignKeys = [
        ForeignKey(
            entity = DestinationEntity::class,
            parentColumns = ["id"],
            childColumns = ["destinationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["destinationId"])]
)
data class ForwardingRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val priority: Int,
    val label: String,
    val senderPattern: String,
    val bodyContains: String? = null,
    val destinationId: Long,
    val customPayloadKeys: String? = null,
    val enabled: Boolean = true
)
