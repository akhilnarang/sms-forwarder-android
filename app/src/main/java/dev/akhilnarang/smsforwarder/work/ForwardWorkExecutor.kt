package dev.akhilnarang.smsforwarder.work

import dev.akhilnarang.smsforwarder.data.DeliveryStatus
import dev.akhilnarang.smsforwarder.data.ForwardRecordGateway
import dev.akhilnarang.smsforwarder.network.ForwardClientInterface
import dev.akhilnarang.smsforwarder.network.ForwardPayloadFactory
import dev.akhilnarang.smsforwarder.settings.SettingsGateway
import dev.akhilnarang.smsforwarder.data.DestinationRepository
import dev.akhilnarang.smsforwarder.data.DestinationEntity
import dev.akhilnarang.smsforwarder.data.ForwardingRuleRepository
import kotlinx.coroutines.flow.first
import org.json.JSONObject

class ForwardWorkExecutor(
    private val recordGateway: ForwardRecordGateway,
    private val settingsGateway: SettingsGateway,
    private val forwardClient: ForwardClientInterface,
    private val destinationRepository: DestinationRepository,
    private val ruleRepository: ForwardingRuleRepository,
    private val payloadFactory: ForwardPayloadFactory
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

        var destination: DestinationEntity? = null
        var payloadToSend = record.payloadJson
        var customKeysMap: Map<String, String> = emptyMap()

        val rules = ruleRepository.getEnabledRules()
        for (rule in rules) {
            val patternStr = Regex.escape(rule.senderPattern).replace("\\*", ".*")
            val regex = Regex(patternStr, RegexOption.IGNORE_CASE)
            
            if (regex.matches(record.senderNormalized)) {
                if (rule.bodyContains.isNullOrEmpty() || record.messageBody.contains(rule.bodyContains, ignoreCase = true)) {
                    destination = destinationRepository.getDestinationById(rule.destinationId)
                    rule.customPayloadKeys?.let {
                        try {
                            val json = JSONObject(it)
                            val map = mutableMapOf<String, String>()
                            json.keys().forEach { key -> map[key] = json.getString(key) }
                            customKeysMap = map
                        } catch (e: Exception) {}
                    }
                    break
                }
            }
        }
        
        if (destination == null) {
            val defaultDestinations = destinationRepository.getAllDestinations().first()
            if (defaultDestinations.isNotEmpty()) {
                destination = defaultDestinations.first()
            }
        }

        val url = destination?.endpointUrl ?: settingsGateway.currentSettings().endpointUrl
        val headerName = destination?.authHeaderName ?: settingsGateway.currentSettings().authHeaderName
        val headerValue = destination?.authHeaderValue ?: settingsGateway.currentSettings().authHeaderValue
        
        if (destination?.payloadTemplate != null) {
             val mockIncoming = dev.akhilnarang.smsforwarder.sms.IncomingSms(
                 senderRaw = record.senderRaw,
                 senderNormalized = record.senderNormalized,
                 body = record.messageBody,
                 receivedAtEpochMs = record.receivedAtEpochMs,
                 subscriptionId = record.subscriptionId,
                 multipart = record.multipart
             )
             payloadToSend = payloadFactory.createCustomJson(destination.payloadTemplate, mockIncoming, customKeysMap)
        }

        // Create a temporary settings object to pass to the client
        val tempSettings = settingsGateway.currentSettings().copy(
            endpointUrl = url,
            authHeaderName = headerName,
            authHeaderValue = headerValue
        )

        // Pass the updated payload inside the record
        val modifiedRecord = record.copy(payloadJson = payloadToSend)

        val result = forwardClient.forward(modifiedRecord, tempSettings)

        return when (result) {
            is dev.akhilnarang.smsforwarder.network.ForwardResult.Success -> {
                recordGateway.markSent(recordId, System.currentTimeMillis())
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
        private const val MAX_RETRIES = 5
    }
}
