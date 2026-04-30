package dev.akhilnarang.smsforwarder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsTab(
    currentState: SmsForwarderUiState,
    hasReceiveSmsPermission: Boolean,
    hasReadSmsPermission: Boolean,
    onRequestSmsPermissions: () -> Unit,
    onSaveSettings: (String, String, String, Int, Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var isAuthHeaderValueVisible by rememberSaveable { mutableStateOf(false) }

    var endpointUrl by rememberSaveable(currentState.settings.endpointUrl) {
        mutableStateOf(currentState.settings.endpointUrl)
    }
    var authHeaderName by rememberSaveable(currentState.settings.authHeaderName) {
        mutableStateOf(currentState.settings.authHeaderName)
    }
    var authHeaderValue by rememberSaveable(currentState.settings.authHeaderValue) {
        mutableStateOf(currentState.settings.authHeaderValue)
    }
    var connectTimeoutSeconds by rememberSaveable(currentState.settings.connectTimeoutSeconds) {
        mutableStateOf(currentState.settings.connectTimeoutSeconds.toString())
    }
    var readTimeoutSeconds by rememberSaveable(currentState.settings.readTimeoutSeconds) {
        mutableStateOf(currentState.settings.readTimeoutSeconds.toString())
    }

    val saveSettings = remember(
        endpointUrl,
        authHeaderName,
        authHeaderValue,
        connectTimeoutSeconds,
        readTimeoutSeconds,
        onSaveSettings,
    ) {
        {
            onSaveSettings(
                endpointUrl,
                authHeaderName,
                authHeaderValue,
                connectTimeoutSeconds.toIntOrNull() ?: DEFAULT_TIMEOUT_SECONDS,
                readTimeoutSeconds.toIntOrNull() ?: DEFAULT_TIMEOUT_SECONDS,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PermissionCard(
            hasReceiveSmsPermission = hasReceiveSmsPermission,
            hasReadSmsPermission = hasReadSmsPermission,
            onRequestSmsPermissions = onRequestSmsPermissions,
        )

        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Server configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = endpointUrl,
                    onValueChange = { endpointUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Endpoint URL") },
                    placeholder = { Text("https://example.com/sms") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = authHeaderName,
                    onValueChange = { authHeaderName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Auth header name") },
                    placeholder = { Text("X-Auth-Token") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = authHeaderValue,
                    onValueChange = { authHeaderValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Auth header value") },
                    placeholder = { Text("secret-value") },
                    visualTransformation =
                        if (isAuthHeaderValueVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    trailingIcon = {
                        IconButton(
                            onClick = { isAuthHeaderValueVisible = !isAuthHeaderValueVisible },
                        ) {
                            Icon(
                                imageVector =
                                    if (isAuthHeaderValueVisible) {
                                        Icons.Rounded.VisibilityOff
                                    } else {
                                        Icons.Rounded.Visibility
                                    },
                                contentDescription =
                                    if (isAuthHeaderValueVisible) {
                                        "Hide auth header value"
                                    } else {
                                        "Show auth header value"
                                    },
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = connectTimeoutSeconds,
                    onValueChange = { connectTimeoutSeconds = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Connect timeout (seconds)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = readTimeoutSeconds,
                    onValueChange = { readTimeoutSeconds = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Read timeout (seconds)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus(force = true)
                            saveSettings()
                        },
                    ),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        saveSettings()
                    },
                ) {
                    Text("Save settings")
                }
                Text(
                    text = "Incoming messages are stored locally first, then forwarded by WorkManager when network is available.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
internal fun PermissionCard(
    hasReceiveSmsPermission: Boolean,
    hasReadSmsPermission: Boolean,
    onRequestSmsPermissions: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "SMS permission",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (hasReceiveSmsPermission && hasReadSmsPermission) {
                    "RECEIVE_SMS and READ_SMS are granted. The app can capture new SMS and browse recent device messages."
                } else {
                    buildString {
                        append("Grant ")
                        if (!hasReceiveSmsPermission) {
                            append("RECEIVE_SMS")
                        }
                        if (!hasReceiveSmsPermission && !hasReadSmsPermission) {
                            append(" and ")
                        }
                        if (!hasReadSmsPermission) {
                            append("READ_SMS")
                        }
                        append(" so the app can capture new SMS and browse historical inbox messages.")
                    }
                },
            )
            if (!hasReceiveSmsPermission || !hasReadSmsPermission) {
                Button(onClick = onRequestSmsPermissions) {
                    Text("Grant SMS permissions")
                }
            }
        }
    }
}

private const val DEFAULT_TIMEOUT_SECONDS = 15
