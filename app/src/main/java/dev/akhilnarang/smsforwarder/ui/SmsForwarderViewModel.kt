package dev.akhilnarang.smsforwarder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.akhilnarang.smsforwarder.data.DestinationEntity
import dev.akhilnarang.smsforwarder.data.DestinationRepository
import dev.akhilnarang.smsforwarder.data.DestinationType
import dev.akhilnarang.smsforwarder.data.ForwardRecordEntity
import dev.akhilnarang.smsforwarder.data.ForwardRecordRepository
import dev.akhilnarang.smsforwarder.data.ForwardSummary
import dev.akhilnarang.smsforwarder.data.ForwardingRuleEntity
import dev.akhilnarang.smsforwarder.data.ForwardingRuleRepository
import dev.akhilnarang.smsforwarder.network.ForwardPayloadFactory
import dev.akhilnarang.smsforwarder.settings.AppSettings
import dev.akhilnarang.smsforwarder.settings.SettingsRepository
import dev.akhilnarang.smsforwarder.sms.DeviceSmsScanner
import dev.akhilnarang.smsforwarder.sms.IncomingSms
import dev.akhilnarang.smsforwarder.work.ForwardWorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SmsForwarderUiState(
    val rules: List<ForwardingRuleEntity> = emptyList(),
    val destinations: List<DestinationEntity> = emptyList(),
    val forwardRecords: List<ForwardRecordEntity> = emptyList(),
    val forwardSummary: ForwardSummary = ForwardSummary(),
    val settings: AppSettings = AppSettings(),
    val feedbackMessage: String? = null,
    val deviceSmsMessages: List<IncomingSms> = emptyList(),
    val smsSearchQuery: String = ""
)

class SmsForwarderViewModel(
    private val ruleRepository: ForwardingRuleRepository,
    private val destinationRepository: DestinationRepository,
    private val recordRepository: ForwardRecordRepository,
    private val settingsRepository: SettingsRepository,
    private val deviceSmsScanner: DeviceSmsScanner,
    private val payloadFactory: ForwardPayloadFactory,
    private val workScheduler: ForwardWorkScheduler,
) : ViewModel() {

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    private val _deviceSmsMessages = MutableStateFlow<List<IncomingSms>>(emptyList())
    private val _smsSearchQuery = MutableStateFlow("")

    val uiState: StateFlow<SmsForwarderUiState> =
        combine(
            ruleRepository.getAllRules(),
            destinationRepository.getAllDestinations(),
            recordRepository.observeAll(),
            recordRepository.observeSummary(),
            settingsRepository.observeSettings(),
            _feedbackMessage,
            _deviceSmsMessages,
            _smsSearchQuery
        ) { args ->
            val rules = args[0] as List<ForwardingRuleEntity>
            val destinations = args[1] as List<DestinationEntity>
            val records = args[2] as List<ForwardRecordEntity>
            val summary = args[3] as ForwardSummary
            val settings = args[4] as AppSettings
            val feedbackMessage = args[5] as String?
            val deviceSmsMessages = args[6] as List<IncomingSms>
            val smsSearchQuery = args[7] as String
            
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
                settings = settings,
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

    fun updateSettings(settings: AppSettings) {
        settingsRepository.saveSettings(
            endpointUrl = settings.endpointUrl,
            authHeaderName = settings.authHeaderName,
            authHeaderValue = settings.authHeaderValue,
            connectTimeoutSeconds = settings.connectTimeoutSeconds,
            readTimeoutSeconds = settings.readTimeoutSeconds
        )
        _feedbackMessage.value = "Settings saved successfully"
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

    fun updateRulePriority(rule: ForwardingRuleEntity, priority: Int) {
        viewModelScope.launch {
            ruleRepository.updateRulePriority(rule, priority)
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

    fun clearQueue() {
        viewModelScope.launch {
            recordRepository.clearAll()
            _feedbackMessage.value = "Queue cleared"
        }
    }

    fun manuallyForwardSms(sms: IncomingSms, rule: dev.akhilnarang.smsforwarder.data.ForwardingRuleEntity) {
        viewModelScope.launch {
            val destination = destinationRepository.getDestinationById(rule.destinationId)
            if (destination == null) {
                _feedbackMessage.value = "Failed: Destination not found for this rule"
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

            var payloadJson = ""
            if (destination.type == dev.akhilnarang.smsforwarder.data.DestinationType.TELEGRAM_PRESET) {
                try {
                    val config = JSONObject(destination.configJson ?: "{}")
                    val chatId = config.optString("chatId", "")
                    val template = if (!destination.payloadTemplate.isNullOrBlank()) destination.payloadTemplate else "<b>From:</b> {{sender}}\n\n{{body}}"
                    val textStr = payloadFactory.createTelegramText(template, sms, customKeysMap)
                    val json = JSONObject()
                    json.put("chat_id", chatId)
                    json.put("text", textStr)
                    json.put("parse_mode", "HTML")
                    payloadJson = json.toString()
                } catch (e: Exception) { }
            } else if (!destination.payloadTemplate.isNullOrBlank()) {
                payloadJson = payloadFactory.createCustomJson(destination.payloadTemplate, sms, customKeysMap)
            } else {
                payloadJson = payloadFactory.createJson(sms, customKeysMap)
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
                        settingsRepository = container.settingsRepository,
                        deviceSmsScanner = container.deviceSmsScanner,
                        payloadFactory = container.payloadFactory,
                        workScheduler = container.workScheduler,
                    ) as T
                }
            }
    }
}
