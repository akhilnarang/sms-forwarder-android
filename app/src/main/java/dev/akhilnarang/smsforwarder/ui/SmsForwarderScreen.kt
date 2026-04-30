package dev.akhilnarang.smsforwarder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.akhilnarang.smsforwarder.data.ForwardRecordEntity
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsForwarderScreen(
    viewModel: SmsForwarderViewModel,
    hasReceiveSmsPermission: Boolean,
    hasReadSmsPermission: Boolean,
    onRequestSmsPermissions: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedRecord by remember { mutableStateOf<ForwardRecordEntity?>(null) }

    LaunchedEffect(uiState.feedbackMessage) {
        val message = uiState.feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearFeedbackMessage()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text("SMS Forwarder") },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding(),
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 8.dp,
            ) {
                listOf("Settings", "Senders", "Queue", "Summary", "Device SMS").forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(label) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                when (selectedTabIndex) {
                    0 ->
                        SettingsTab(
                            currentState = uiState,
                            hasReceiveSmsPermission = hasReceiveSmsPermission,
                            hasReadSmsPermission = hasReadSmsPermission,
                            onRequestSmsPermissions = onRequestSmsPermissions,
                            onSaveSettings = { url, name, value, connect, read ->
                                viewModel.saveSettings(url, name, value, connect, read)
                            },
                        )
                    1 ->
                        SendersTab(
                            senders = uiState.senders,
                            onAddSender = viewModel::addSender,
                            onSetSenderEnabled = viewModel::setSenderEnabled,
                            onDeleteSender = viewModel::deleteSender,
                        )
                    2 ->
                        QueueTab(
                            records = uiState.records,
                            onRetryRecord = viewModel::retryRecord,
                            onOpenRecord = { selectedRecord = it },
                        )
                    3 -> SummaryTab(summary = uiState.summary)
                    else ->
                        DeviceSmsTab(
                            hasReadSmsPermission = hasReadSmsPermission,
                            messages = uiState.deviceSmsMessages,
                            searchQuery = uiState.smsSearchQuery,
                            onRequestSmsPermissions = onRequestSmsPermissions,
                            onLoadDeviceSms = viewModel::loadDeviceSms,
                            onSearchQueryChange = viewModel::updateSmsSearchQuery,
                            onForwardSms = viewModel::manuallyForwardSms,
                        )
                }
            }
        }
    }

    selectedRecord?.let { record ->
        RecordDetailDialog(
            record = record,
            onDismiss = { selectedRecord = null },
        )
    }
}
