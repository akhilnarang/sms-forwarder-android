package dev.akhilnarang.smsforwarder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.akhilnarang.smsforwarder.data.ForwardRecordEntity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
    var selectedRecord by remember { mutableStateOf<ForwardRecordEntity?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    val tabs = listOf(
        "Destinations" to Icons.Rounded.Send,
        "Rules" to Icons.Rounded.Rule,
        "Queue" to Icons.Rounded.List,
        "Summary" to Icons.Rounded.Assessment,
        "Device SMS" to Icons.Rounded.PhoneAndroid
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })

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
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 8.dp,
            ) {
                tabs.forEachIndexed { index, (label, icon) ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = { Text(label) },
                        icon = { Icon(icon, contentDescription = label) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    when (page) {
                        0 ->
                            DestinationsTab(
                                destinations = uiState.destinations,
                                rules = uiState.rules,
                                onAddDestination = { label, type, url, authName, authVal, payload, config -> 
                                    viewModel.addDestination(
                                        dev.akhilnarang.smsforwarder.data.DestinationEntity(
                                            label = label,
                                            type = type,
                                            endpointUrl = url,
                                            authHeaderName = authName.takeIf { it.isNotBlank() },
                                            authHeaderValue = authVal.takeIf { it.isNotBlank() },
                                            payloadTemplate = payload.takeIf { it.isNotBlank() },
                                            configJson = config.takeIf { it.isNotBlank() },
                                            enabled = true
                                        )
                                    )
                                },
                                onEditDestination = { id, label, type, url, authName, authVal, payload, config -> 
                                    viewModel.updateDestination(
                                        dev.akhilnarang.smsforwarder.data.DestinationEntity(
                                            id = id,
                                            label = label,
                                            type = type,
                                            endpointUrl = url,
                                            authHeaderName = authName.takeIf { it.isNotBlank() },
                                            authHeaderValue = authVal.takeIf { it.isNotBlank() },
                                            payloadTemplate = payload.takeIf { it.isNotBlank() },
                                            configJson = config.takeIf { it.isNotBlank() },
                                            enabled = uiState.destinations.find { it.id == id }?.enabled ?: true
                                        )
                                    )
                                },
                                onSetDestinationEnabled = viewModel::setDestinationEnabled,
                                onDeleteDestination = { id -> 
                                    uiState.destinations.find { it.id == id }?.let { 
                                        viewModel.deleteDestination(it) 
                                    }
                                },
                            )
                        1 ->
                            RulesTab(
                                rules = uiState.rules,
                                destinations = uiState.destinations,
                                onAddRule = viewModel::addRule,
                                onEditRule = viewModel::updateRule,
                                onSetRuleEnabled = viewModel::setRuleEnabled,
                                onDeleteRule = viewModel::deleteRule,
                                onMoveRuleUp = viewModel::moveRuleUp,
                                onMoveRuleDown = viewModel::moveRuleDown,
                            )
                        2 ->
                            QueueTab(
                                records = uiState.forwardRecords,
                                onRetryRecord = { viewModel.resendRecord(it) },
                                onOpenRecord = { selectedRecord = it },
                                onClearQueue = viewModel::clearQueue,
                            )
                        3 -> SummaryTab(summary = uiState.forwardSummary)
                        else ->
                            DeviceSmsTab(
                                hasReadSmsPermission = hasReadSmsPermission,
                                messages = uiState.deviceSmsMessages,
                                rules = uiState.rules,
                                destinations = uiState.destinations,
                                searchQuery = uiState.smsSearchQuery,
                                onRequestSmsPermissions = onRequestSmsPermissions,
                                onLoadDeviceSms = { viewModel.loadDeviceSms(500) },
                                onSearchQueryChange = viewModel::updateSmsSearchQuery,
                                onForwardSms = viewModel::manuallyForwardSms,
                            )
                    }
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
