package dev.akhilnarang.smsforwarder.work

import dev.akhilnarang.smsforwarder.data.DeliveryStatus
import dev.akhilnarang.smsforwarder.data.ForwardRecordGateway
import dev.akhilnarang.smsforwarder.network.ForwardClientInterface
import dev.akhilnarang.smsforwarder.network.ForwardResult
import dev.akhilnarang.smsforwarder.settings.SettingsGateway

/**
 * Core forwarding logic extracted from ForwardWorker for testability.
 * Handles record claiming, forwarding, and status transitions.
 */
class ForwardWorkExecutor(
    private val recordGateway: ForwardRecordGateway,
    private val settingsGateway: SettingsGateway,
    private val forwardClient: ForwardClientInterface,
    private val maxRetryAttempts: Int = MAX_AUTOMATIC_RETRY_ATTEMPTS,
) {
    suspend fun execute(recordId: Long, runAttemptCount: Int): WorkResult {
        val claimed = recordGateway.markSendingIfEligible(
            recordId,
            System.currentTimeMillis(),
        )
        if (!claimed) {
            return WorkResult.SUCCESS
        }

        val record = recordGateway.getById(recordId) ?: return WorkResult.FAILURE
        if (record.status == DeliveryStatus.SENT) {
            return WorkResult.SUCCESS
        }

        val settings = settingsGateway.currentSettings()

        return when (val result = forwardClient.forward(record, settings)) {
            ForwardResult.Success -> {
                recordGateway.markSent(recordId, System.currentTimeMillis())
                WorkResult.SUCCESS
            }
            is ForwardResult.RetryableFailure -> {
                if (runAttemptCount + 1 >= maxRetryAttempts) {
                    recordGateway.markFailed(recordId, result.message)
                    WorkResult.SUCCESS
                } else {
                    recordGateway.markRetrying(recordId, result.message)
                    WorkResult.RETRY
                }
            }
            is ForwardResult.PermanentFailure -> {
                recordGateway.markFailed(recordId, result.message)
                WorkResult.SUCCESS
            }
        }
    }

    enum class WorkResult { SUCCESS, FAILURE, RETRY }

    companion object {
        const val MAX_AUTOMATIC_RETRY_ATTEMPTS = 5
    }
}
