package dev.akhilnarang.smsforwarder.data

import dev.akhilnarang.smsforwarder.sms.IncomingSms
import dev.akhilnarang.smsforwarder.data.ForwardingRuleEntity
import kotlinx.coroutines.flow.Flow

class ForwardRecordRepository(
    private val dao: ForwardRecordDao,
) : ForwardRecordGateway {
    fun observeAll(): Flow<List<ForwardRecordEntity>> = dao.observeAll()

    fun observeSummary(): Flow<ForwardSummary> = dao.observeSummary()

    override suspend fun getById(id: Long): ForwardRecordEntity? = dao.getById(id)

    suspend fun insertIncoming(
        incomingSms: IncomingSms,
        matchedRule: ForwardingRuleEntity?,
        destinationId: Long?,
        payloadJson: String,
    ): Long =
        dao.insert(
            ForwardRecordEntity(
                senderRaw = incomingSms.senderRaw,
                senderNormalized = incomingSms.senderNormalized,
                messageBody = incomingSms.body,
                receivedAtEpochMs = incomingSms.receivedAtEpochMs,
                subscriptionId = incomingSms.subscriptionId,
                multipart = incomingSms.multipart,
                payloadJson = payloadJson,
                destinationId = destinationId,
                status = if (matchedRule != null) DeliveryStatus.PENDING else DeliveryStatus.IGNORED,
                statusReason = matchedRule?.let { "Matched rule: ${it.label}" }
                    ?: "Rule not matched",
                responseDetails = null,
            ),
        )

    suspend fun insertManualIncoming(
        incomingSms: IncomingSms,
        matchedRule: ForwardingRuleEntity?,
        destinationId: Long?,
        payloadJson: String,
    ): Long =
        dao.insert(
            ForwardRecordEntity(
                senderRaw = incomingSms.senderRaw,
                senderNormalized = incomingSms.senderNormalized,
                messageBody = incomingSms.body,
                receivedAtEpochMs = incomingSms.receivedAtEpochMs,
                subscriptionId = incomingSms.subscriptionId,
                multipart = incomingSms.multipart,
                payloadJson = payloadJson,
                destinationId = destinationId,
                status = DeliveryStatus.PENDING,
                statusReason = matchedRule?.let { "Manually forwarded from device SMS: ${it.label}" }
                    ?: "Manually forwarded from device SMS inbox",
                responseDetails = null,
            ),
        )

    /**
     * Inserts a synthetic test record that is clearly marked as non-real.
     * The returned ID should be enqueued via [ForwardWorkScheduler] to exercise the full
     * forwarding path without waiting for real SMS.
     */
    suspend fun insertTestRecord(
        senderTag: String,
        messageBody: String,
        receivedAtEpochMs: Long,
        payloadJson: String,
    ): Long =
        dao.insert(
            ForwardRecordEntity(
                senderRaw = senderTag,
                senderNormalized = senderTag,
                messageBody = messageBody,
                receivedAtEpochMs = receivedAtEpochMs,
                subscriptionId = null,
                multipart = false,
                payloadJson = payloadJson,
                status = DeliveryStatus.PENDING,
                statusReason = "Manually enqueued via in-app validation helper",
                responseDetails = null,
                isTestRecord = true,
            ),
        )

    suspend fun markPending(id: Long) {
        dao.updateStatus(id, DeliveryStatus.PENDING)
    }

    override suspend fun markSendingIfEligible(id: Long, attemptedAtEpochMs: Long): Boolean =
        dao.markSendingIfEligible(id = id, attemptedAtEpochMs = attemptedAtEpochMs) > 0

    override suspend fun markFailed(id: Long, error: String) {
        dao.updateStatusWithError(
            id = id,
            error = error,
            status = DeliveryStatus.FAILED,
        )
    }

    override suspend fun markRetrying(id: Long, error: String) {
        dao.updateStatusWithError(
            id = id,
            error = error,
            status = DeliveryStatus.RETRYING,
        )
    }

    override suspend fun markSent(id: Long, sentAtEpochMs: Long, responseDetails: String?) {
        dao.markSent(id = id, sentAtEpochMs = sentAtEpochMs, responseDetails = responseDetails)
    }
}
