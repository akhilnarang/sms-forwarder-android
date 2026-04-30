package dev.akhilnarang.smsforwarder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "forward_records",
    indices = [Index(value = ["status"])],
)
data class ForwardRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderRaw: String,
    val senderNormalized: String,
    val messageBody: String,
    val receivedAtEpochMs: Long,
    val subscriptionId: Int?,
    val multipart: Boolean,
    val payloadJson: String,
    val status: DeliveryStatus,
    val statusReason: String,
    val lastError: String?,
    val attemptCount: Int = 0,
    val lastAttemptedAtEpochMs: Long? = null,
    val sentAtEpochMs: Long? = null,
    /** True for records created via the in-app validation helper, not from a real incoming SMS. */
    val isTestRecord: Boolean = false,
)

data class ForwardSummary(
    val totalCount: Int = 0,
    val matchedCount: Int = 0,
    val sentCount: Int = 0,
    val failedCount: Int = 0,
    val pendingCount: Int = 0,
    val ignoredCount: Int = 0,
)
