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
            SmsForwarderUiState(
                rules = rules,
                destinations = destinations,
                forwardRecords = records,
                forwardSummary = summary,
                settings = settings,
                feedbackMessage = feedbackMessage,
                deviceSmsMessages = deviceSmsMessages,
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

    fun manuallyForwardSms(sms: IncomingSms) {
        viewModelScope.launch {
            val rules = ruleRepository.getEnabledRules()
            var matchedRule: ForwardingRuleEntity? = null

            for (rule in rules) {
                val patternStr = Regex.escape(rule.senderPattern).replace("\\*", ".*")
                val regex = Regex(patternStr, RegexOption.IGNORE_CASE)
                
                if (regex.matches(sms.senderNormalized)) {
                    if (rule.bodyContains.isNullOrEmpty() || sms.body.contains(rule.bodyContains, ignoreCase = true)) {
                        matchedRule = rule
                        break
                    }
                }
            }

            val payloadJson = payloadFactory.createJson(sms)
            val recordId =
                recordRepository.insertManualIncoming(
                    incomingSms = sms,
                    matchedRule = matchedRule,
                    payloadJson = payloadJson,
                )

            workScheduler.enqueue(recordId)
            _feedbackMessage.value = "SMS queued for forwarding"
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
