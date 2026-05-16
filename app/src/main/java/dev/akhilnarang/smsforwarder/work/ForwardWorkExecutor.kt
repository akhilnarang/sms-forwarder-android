package dev.akhilnarang.smsforwarder.work

import dev.akhilnarang.smsforwarder.data.DeliveryStatus
import dev.akhilnarang.smsforwarder.data.DestinationRepository
import dev.akhilnarang.smsforwarder.data.ForwardRecordGateway
import dev.akhilnarang.smsforwarder.network.ForwardClientInterface

import org.json.JSONObject

class ForwardWorkExecutor(
    private val recordGateway: ForwardRecordGateway,
    private val forwardClient: ForwardClientInterface,
    private val destinationRepository: DestinationRepository
) {
    enum class WorkResult {
        SUCCESS,
        FAILURE,
        RETRY,
    }

    suspend fun execute(recordId: Long, runAttemptCount: Int): WorkResult {
        val record = recordGateway.getById(recordId) ?: return WorkResult.FAILURE

        if (record.status != DeliveryStatus.PENDING && record.status != DeliveryStatus.RETRYING) {
            return WorkResult.SUCCESS
        }

        val attemptedAtEpochMs = System.currentTimeMillis()
        if (!recordGateway.markSendingIfEligible(recordId, attemptedAtEpochMs)) {
            return WorkResult.FAILURE
        }

        if (record.destinationId == null) {
            recordGateway.markFailed(recordId, "No destination configured for this record")
            return WorkResult.FAILURE
        }
        val destination = destinationRepository.getEnabledDestinationById(record.destinationId)
            ?: run {
                recordGateway.markFailed(recordId, "Destination is missing or disabled")
                return WorkResult.FAILURE
            }

        var url = destination.endpointUrl
        var headerName = destination.authHeaderName ?: ""
        var headerValue = destination.authHeaderValue ?: ""

        if (destination.type == dev.akhilnarang.smsforwarder.data.DestinationType.TELEGRAM_PRESET) {
            try {
                val config = JSONObject(destination.configJson ?: "{}")
                val botToken = config.optString("botToken", "")
                url = "https://api.telegram.org/bot${botToken}/sendMessage"
                headerName = "Content-Type"
                headerValue = "application/json"
            } catch (e: Exception) { }
        }
        
        // Use the exact payload saved in the record during SmsProcessor
        val payloadToSend = record.payloadJson

        // Pass the updated payload inside the record
        val modifiedRecord = record.copy(payloadJson = payloadToSend)

        val result = forwardClient.forward(modifiedRecord, url, headerName, headerValue)

        return when (result) {
            is dev.akhilnarang.smsforwarder.network.ForwardResult.Success -> {
                recordGateway.markSent(recordId, System.currentTimeMillis(), result.responseDetails)
                WorkResult.SUCCESS
            }
            is dev.akhilnarang.smsforwarder.network.ForwardResult.RetryableFailure -> {
                if (runAttemptCount < MAX_RETRIES) {
                    recordGateway.markRetrying(recordId, result.message)
                    WorkResult.RETRY
                } else {
                    recordGateway.markFailed(recordId, result.message)
                    WorkResult.FAILURE
                }
            }
            is dev.akhilnarang.smsforwarder.network.ForwardResult.PermanentFailure -> {
                recordGateway.markFailed(recordId, result.message)
                WorkResult.FAILURE
            }
        }
    }

    companion object {
        private const val MAX_RETRIES = 4
    }
}
