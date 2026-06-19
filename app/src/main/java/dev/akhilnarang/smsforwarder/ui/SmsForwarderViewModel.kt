package dev.akhilnarang.smsforwarder.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.akhilnarang.smsforwarder.data.BackupManager
import dev.akhilnarang.smsforwarder.data.BackupValidationException
import dev.akhilnarang.smsforwarder.data.DestinationEntity
import dev.akhilnarang.smsforwarder.data.DestinationRepository
import dev.akhilnarang.smsforwarder.data.ForwardRecordEntity
import dev.akhilnarang.smsforwarder.data.ForwardRecordRepository
import dev.akhilnarang.smsforwarder.data.ForwardSummary
import dev.akhilnarang.smsforwarder.data.ForwardingRuleEntity
import dev.akhilnarang.smsforwarder.data.ForwardingRuleRepository
import dev.akhilnarang.smsforwarder.data.ImportMode
import dev.akhilnarang.smsforwarder.network.ForwardPayloadFactory
import dev.akhilnarang.smsforwarder.sms.DeviceSmsScanner
import dev.akhilnarang.smsforwarder.sms.IncomingSms
import dev.akhilnarang.smsforwarder.work.ForwardWorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

data class SmsForwarderUiState(
    val rules: List<ForwardingRuleEntity> = emptyList(),
    val destinations: List<DestinationEntity> = emptyList(),
    val forwardRecords: List<ForwardRecordEntity> = emptyList(),
    val forwardSummary: ForwardSummary = ForwardSummary(),
    val feedbackMessage: String? = null,
    val deviceSmsMessages: List<IncomingSms> = emptyList(),
    val smsSearchQuery: String = ""
)

class SmsForwarderViewModel(
    private val ruleRepository: ForwardingRuleRepository,
    private val destinationRepository: DestinationRepository,
    private val recordRepository: ForwardRecordRepository,
    private val deviceSmsScanner: DeviceSmsScanner,
    private val payloadFactory: ForwardPayloadFactory,
    private val workScheduler: ForwardWorkScheduler,
    private val backupManager: BackupManager,
) : ViewModel() {

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    private val _deviceSmsMessages = MutableStateFlow<List<IncomingSms>>(emptyList())
    private val _smsSearchQuery = MutableStateFlow("")

    private data class StoredData(
        val rules: List<ForwardingRuleEntity>,
        val destinations: List<DestinationEntity>,
        val records: List<ForwardRecordEntity>,
        val summary: ForwardSummary,
    )

    val uiState: StateFlow<SmsForwarderUiState> =
        combine(
            combine(
                ruleRepository.getAllRules(),
                destinationRepository.getAllDestinations(),
                recordRepository.observeAll(),
                recordRepository.observeSummary(),
            ) { rules, destinations, records, summary ->
                StoredData(rules, destinations, records, summary)
            },
            _feedbackMessage,
            _deviceSmsMessages,
            _smsSearchQuery
        ) { stored, feedbackMessage, deviceSmsMessages, smsSearchQuery ->
            val rules = stored.rules
            val destinations = stored.destinations
            val records = stored.records
            val summary = stored.summary

            val filteredDeviceSms = if (smsSearchQuery.isBlank()) {
                deviceSmsMessages
            } else {
                val lowercaseQuery = smsSearchQuery.lowercase()
                deviceSmsMessages.filter { message ->
                    message.senderRaw.lowercase().contains(lowercaseQuery) ||
                        message.senderNormalized.lowercase().contains(lowercaseQuery)
                }
            }
            
            SmsForwarderUiState(
                rules = rules,
                destinations = destinations,
                forwardRecords = records,
                forwardSummary = summary,
                feedbackMessage = feedbackMessage,
                deviceSmsMessages = filteredDeviceSms,
                smsSearchQuery = smsSearchQuery
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SmsForwarderUiState(),
        )

    fun clearFeedbackMessage() {
        _feedbackMessage.value = null
    }

    // Destinations
    fun addDestination(destination: DestinationEntity) {
        viewModelScope.launch {
            destinationRepository.addDestination(destination)
        }
    }

    fun updateDestination(destination: DestinationEntity) {
        viewModelScope.launch {
            destinationRepository.updateDestination(destination)
        }
    }

    fun deleteDestination(destination: DestinationEntity) {
        viewModelScope.launch {
            destinationRepository.deleteDestination(destination)
        }
    }

    fun setDestinationEnabled(destination: DestinationEntity, enabled: Boolean) {
        viewModelScope.launch {
            destinationRepository.updateDestination(destination.copy(enabled = enabled))
        }
    }

    // Rules
    fun addRule(rule: ForwardingRuleEntity) {
        viewModelScope.launch {
            ruleRepository.addRule(rule)
        }
    }

    fun updateRule(rule: ForwardingRuleEntity) {
        viewModelScope.launch {
            ruleRepository.updateRule(rule)
        }
    }

    fun deleteRule(rule: ForwardingRuleEntity) {
        viewModelScope.launch {
            ruleRepository.deleteRule(rule)
        }
    }

    fun setRuleEnabled(rule: ForwardingRuleEntity, enabled: Boolean) {
        viewModelScope.launch {
            ruleRepository.setEnabled(rule, enabled)
        }
    }

    fun moveRuleUp(rule: ForwardingRuleEntity) {
        viewModelScope.launch {
            ruleRepository.swapPriorityWithNeighbor(rule, direction = -1)
        }
    }

    fun moveRuleDown(rule: ForwardingRuleEntity) {
        viewModelScope.launch {
            ruleRepository.swapPriorityWithNeighbor(rule, direction = 1)
        }
    }

    fun resendRecord(record: ForwardRecordEntity) {
        viewModelScope.launch {
            recordRepository.markPending(record.id)
            workScheduler.enqueue(record.id)
        }
    }

    fun loadDeviceSms(limit: Int) {
        viewModelScope.launch {
            _deviceSmsMessages.value = deviceSmsScanner.getRecentSms(limit)
        }
    }

    fun updateSmsSearchQuery(query: String) {
        _smsSearchQuery.value = query
    }

    fun runBackup(writer: suspend (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                val json = backupManager.exportToJson()
                writer(json)
                "Backup saved"
            } catch (e: Exception) {
                Log.w("SmsForwarderViewModel", "Backup failed", e)
                "Backup failed: ${e.message ?: e.javaClass.simpleName}"
            }
            _feedbackMessage.value = message
        }
    }

    fun runRestore(mode: ImportMode, reader: suspend () -> String?) {
        viewModelScope.launch {
            val message = try {
                val jsonString = reader()
                    ?: throw BackupValidationException("Could not read backup file")
                val result = backupManager.importFromJson(jsonString, mode)
                val summary = buildString {
                    append("Restored ")
                    append(result.destinationsImported)
                    append(" destination(s) and ")
                    append(result.rulesImported)
                    append(" rule(s)")
                    if (result.rulesSkipped > 0) {
                        append(" (")
                        append(result.rulesSkipped)
                        append(" skipped)")
                    }
                }
                summary
            } catch (e: BackupValidationException) {
                "Restore failed: ${e.message}"
            } catch (e: Exception) {
                Log.w("SmsForwarderViewModel", "Restore failed", e)
                "Restore failed: ${e.message ?: e.javaClass.simpleName}"
            }
            _feedbackMessage.value = message
        }
    }

    fun clearQueue() {
        viewModelScope.launch {
            recordRepository.clearAll()
            _feedbackMessage.value = "Queue cleared"
        }
    }

    fun manuallyForwardSms(sms: IncomingSms, rule: dev.akhilnarang.smsforwarder.data.ForwardingRuleEntity) {
        viewModelScope.launch {
            val destination = destinationRepository.getEnabledDestinationById(rule.destinationId)
            if (destination == null) {
                _feedbackMessage.value = "Failed: Destination is disabled or missing"
                return@launch
            }

            var customKeysMap: Map<String, String> = emptyMap()
            rule.customPayloadKeys?.let {
                try {
                    val jsonObj = JSONObject(it)
                    val map = mutableMapOf<String, String>()
                    jsonObj.keys().forEach { key -> map[key] = jsonObj.getString(key) }
                    customKeysMap = map
                } catch (e: Exception) {}
            }

            val payloadJson = try {
                payloadFactory.buildPayloadFor(destination, sms, customKeysMap)
            } catch (e: Exception) {
                Log.w("SmsForwarderViewModel", "Failed to build payload for rule ${rule.id}", e)
                val failedId = recordRepository.insertManualIncoming(
                    incomingSms = sms,
                    matchedRule = rule,
                    destinationId = destination.id,
                    payloadJson = "",
                )
                recordRepository.markFailed(failedId, "Payload build failed: ${e.message}")
                _feedbackMessage.value = "Failed: Payload build failed"
                return@launch
            }

            val recordId =
                recordRepository.insertManualIncoming(
                    incomingSms = sms,
                    matchedRule = rule,
                    destinationId = destination.id,
                    payloadJson = payloadJson,
                )

            workScheduler.enqueue(recordId)
            _feedbackMessage.value = "SMS queued for manual forwarding using rule ${rule.label}"
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: androidx.lifecycle.viewmodel.CreationExtras,
                ): T {
                    val application =
                        checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as dev.akhilnarang.smsforwarder.SmsForwarderApp
                    val container = application.container

                    return SmsForwarderViewModel(
                        ruleRepository = container.ruleRepository,
                        destinationRepository = container.destinationRepository,
                        recordRepository = container.forwardRecordRepository,
                        deviceSmsScanner = container.deviceSmsScanner,
                        payloadFactory = container.payloadFactory,
                        workScheduler = container.workScheduler,
                        backupManager = container.backupManager,
                    ) as T
                }
            }
    }
}
