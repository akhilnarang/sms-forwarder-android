package dev.akhilnarang.smsforwarder.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.akhilnarang.smsforwarder.AppContainer
import dev.akhilnarang.smsforwarder.data.ConfiguredSenderEntity
import dev.akhilnarang.smsforwarder.data.ForwardRecordEntity
import dev.akhilnarang.smsforwarder.data.ForwardRecordRepository
import dev.akhilnarang.smsforwarder.data.ForwardSummary
import dev.akhilnarang.smsforwarder.data.ConfiguredSenderRepository
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
    val settings: AppSettings = AppSettings(),
    val senders: List<ConfiguredSenderEntity> = emptyList(),
    val records: List<ForwardRecordEntity> = emptyList(),
    val deviceSmsMessages: List<IncomingSms> = emptyList(),
    val smsSearchQuery: String = "",
    val summary: ForwardSummary = ForwardSummary(),
    val feedbackMessage: String? = null,
)

class SmsForwarderViewModel(
    private val settingsRepository: SettingsRepository,
    private val senderRepository: ConfiguredSenderRepository,
    private val forwardRecordRepository: ForwardRecordRepository,
    private val workScheduler: ForwardWorkScheduler,
    private val payloadFactory: ForwardPayloadFactory,
    private val deviceSmsScanner: DeviceSmsScanner,
) : ViewModel() {
    private val feedbackMessage = MutableStateFlow<String?>(null)
    private val deviceSmsList = MutableStateFlow<List<IncomingSms>>(emptyList())
    private val smsSearchQuery = MutableStateFlow("")

    private val filteredDeviceSms =
        combine(deviceSmsList, smsSearchQuery) { messages, query ->
            val normalizedQuery = query.trim()
            if (normalizedQuery.isBlank()) {
                messages
            } else {
                val lowercaseQuery = normalizedQuery.lowercase()
                messages.filter { message ->
                    message.senderRaw.lowercase().contains(lowercaseQuery) ||
                        message.senderNormalized.lowercase().contains(lowercaseQuery)
                }
            }
        }

    private val baseUiState =
        combine(
            settingsRepository.observeSettings(),
            senderRepository.observeAll(),
            forwardRecordRepository.observeAll(),
            forwardRecordRepository.observeSummary(),
            feedbackMessage,
        ) { settings, senders, records, summary, message ->
            SmsForwarderUiState(
                settings = settings,
                senders = senders,
                records = records,
                summary = summary,
                feedbackMessage = message,
            )
        }

    val uiState: StateFlow<SmsForwarderUiState> =
        combine(
            baseUiState,
            filteredDeviceSms,
            smsSearchQuery,
        ) { baseUiState, deviceSmsMessages, smsSearchQueryValue ->
            baseUiState.copy(
                deviceSmsMessages = deviceSmsMessages,
                smsSearchQuery = smsSearchQueryValue,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SmsForwarderUiState(),
        )

    fun saveSettings(
        endpointUrl: String,
        authHeaderName: String,
        authHeaderValue: String,
        connectTimeoutSeconds: Int,
        readTimeoutSeconds: Int,
    ) {
        viewModelScope.launch {
            try {
                settingsRepository.saveSettings(
                    endpointUrl,
                    authHeaderName,
                    authHeaderValue,
                    connectTimeoutSeconds,
                    readTimeoutSeconds,
                )
                feedbackMessage.value = "Settings saved."
            } catch (error: IllegalArgumentException) {
                feedbackMessage.value = error.message
            }
        }
    }

    fun addSender(
        label: String,
        rawSender: String,
    ) {
        viewModelScope.launch {
            try {
                senderRepository.addSender(label, rawSender)
                feedbackMessage.value = "Sender added."
            } catch (error: IllegalArgumentException) {
                feedbackMessage.value = error.message
            }
        }
    }

    fun setSenderEnabled(
        sender: ConfiguredSenderEntity,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            senderRepository.setEnabled(sender, enabled)
            feedbackMessage.value = if (enabled) "Sender enabled." else "Sender disabled."
        }
    }

    fun deleteSender(id: Long) {
        viewModelScope.launch {
            senderRepository.deleteSender(id)
            feedbackMessage.value = "Sender removed."
        }
    }

    fun retryRecord(id: Long) {
        viewModelScope.launch {
            forwardRecordRepository.markPending(id)
            workScheduler.retryNow(id)
            feedbackMessage.value = "Retry scheduled."
        }
    }

    fun loadDeviceSms() {
        viewModelScope.launch {
            try {
                deviceSmsList.value = deviceSmsScanner.getRecentSms()
            } catch (_: SecurityException) {
                feedbackMessage.value = "READ_SMS permission is required to load device SMS."
            } catch (_: Exception) {
                feedbackMessage.value = "Unable to load device SMS right now."
            }
        }
    }

    fun updateSmsSearchQuery(query: String) {
        smsSearchQuery.value = query
    }

    fun manuallyForwardSms(incomingSms: IncomingSms) {
        viewModelScope.launch {
            val matchedSender =
                senderRepository.getMatchingEnabledSender(incomingSms.senderNormalized)
            val payloadJson = payloadFactory.createJson(incomingSms)
            val recordId =
                forwardRecordRepository.insertManualIncoming(
                    incomingSms = incomingSms,
                    matchedSender = matchedSender,
                    payloadJson = payloadJson,
                )
            workScheduler.enqueue(recordId)
            feedbackMessage.value = "Message queued for forwarding."
        }
    }

    fun clearFeedbackMessage() {
        feedbackMessage.value = null
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SmsForwarderViewModel(
                        settingsRepository = container.settingsRepository,
                        senderRepository = container.senderRepository,
                        forwardRecordRepository = container.forwardRecordRepository,
                        workScheduler = container.workScheduler,
                        payloadFactory = container.payloadFactory,
                        deviceSmsScanner = container.deviceSmsScanner,
                    )
                }
            }
    }
}
