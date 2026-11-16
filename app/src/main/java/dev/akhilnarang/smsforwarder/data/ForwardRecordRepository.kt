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

    suspend fun clearAll() = dao.deleteAll()

    /**
     * Inserts an incoming SMS as a forward record for delivery.
     *
     * Args:
     *   incomingSms: The incoming SMS to store.
     *   matchedRule: The rule that matched, or null when no rule matched.
     *   destinationId: The destination of the matched rule.
     *   payloadJson: The built forward payload.
     *
     * Returns:
     *   The ID of the new record.
     */
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

    /**
     * Inserts an incoming SMS as a FAILED record in one database write.
     *
     * Use this when the payload build fails before enqueue. A single write stops the
     * startup scan from selecting or recovering an intermediate PENDING record.
     *
     * Args:
     *   incomingSms: The incoming SMS to store.
     *   matchedRule: The rule that matched, or null when no rule matched.
     *   destinationId: The destination of the matched rule.
     *   error: The failure reason. Must not be empty.
     *
     * Returns:
     *   The ID of the new FAILED record.
     */
    suspend fun insertFailedIncoming(
        incomingSms: IncomingSms,
        matchedRule: ForwardingRuleEntity?,
        destinationId: Long?,
        error: String,
    ): Long =
        dao.insert(
            ForwardRecordEntity(
                senderRaw = incomingSms.senderRaw,
                senderNormalized = incomingSms.senderNormalized,
                messageBody = incomingSms.body,
                receivedAtEpochMs = incomingSms.receivedAtEpochMs,
                subscriptionId = incomingSms.subscriptionId,
                multipart = incomingSms.multipart,
                payloadJson = "",
                destinationId = destinationId,
                status = DeliveryStatus.FAILED,
                statusReason = matchedRule?.let { "Matched rule: ${it.label}" }
                    ?: "Rule not matched",
                responseDetails = error,
            ),
        )

    /**
     * Inserts a manually selected incoming SMS as a forward record.
     *
     * Args:
     *   incomingSms: The incoming SMS to store.
     *   matchedRule: The rule used for the manual forward.
     *   destinationId: The destination of that rule.
     *   payloadJson: The built forward payload.
     *
     * Returns:
     *   The ID of the new record.
     */
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
     * Inserts a manually selected incoming SMS as a FAILED record in one database write.
     *
     * Use this when the payload build fails before enqueue. A single write stops the
     * startup scan from selecting or recovering an intermediate PENDING record.
     *
     * Args:
     *   incomingSms: The incoming SMS to store.
     *   matchedRule: The rule used for the manual forward.
     *   destinationId: The destination of that rule.
     *   error: The failure reason. Must not be empty.
     *
     * Returns:
     *   The ID of the new FAILED record.
     */
    suspend fun insertFailedManualIncoming(
        incomingSms: IncomingSms,
        matchedRule: ForwardingRuleEntity?,
        destinationId: Long?,
        error: String,
    ): Long =
        dao.insert(
            ForwardRecordEntity(
                senderRaw = incomingSms.senderRaw,
                senderNormalized = incomingSms.senderNormalized,
                messageBody = incomingSms.body,
                receivedAtEpochMs = incomingSms.receivedAtEpochMs,
                subscriptionId = incomingSms.subscriptionId,
                multipart = incomingSms.multipart,
                payloadJson = "",
                destinationId = destinationId,
                status = DeliveryStatus.FAILED,
                statusReason = matchedRule?.let { "Manually forwarded from device SMS: ${it.label}" }
                    ?: "Manually forwarded from device SMS inbox",
                responseDetails = error,
            ),
        )

    /**
     * Inserts a synthetic test record that is clearly marked as non-real.
     *
     * The caller must enqueue the returned ID to exercise the full forwarding path.
     *
     * Args:
     *   senderTag: The synthetic sender label.
     *   messageBody: The synthetic message body.
     *   receivedAtEpochMs: The receive time to store.
     *   payloadJson: The payload to store.
     *
     * Returns:
     *   The ID of the new test record.
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

    override suspend fun markSendingIfEligible(
        id: Long,
        attemptedAtEpochMs: Long,
        staleSendingBeforeEpochMs: Long,
    ): Boolean =
        dao.markSendingIfEligible(
            id = id,
            attemptedAtEpochMs = attemptedAtEpochMs,
            staleSendingBeforeEpochMs = staleSendingBeforeEpochMs,
        ) > 0

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

    override suspend fun countByStatus(status: DeliveryStatus): Int = dao.countByStatus(status)

    override suspend fun getRecoverableRecordIds(staleSendingBeforeEpochMs: Long): List<Long> =
        dao.getRecoverableRecordIds(staleSendingBeforeEpochMs)
}
