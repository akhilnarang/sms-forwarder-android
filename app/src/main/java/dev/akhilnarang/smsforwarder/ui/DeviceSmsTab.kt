package dev.akhilnarang.smsforwarder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akhilnarang.smsforwarder.sms.IncomingSms

@Composable
internal fun DeviceSmsTab(
    hasReadSmsPermission: Boolean,
    messages: List<IncomingSms>,
    searchQuery: String,
    onRequestSmsPermissions: () -> Unit,
    onLoadDeviceSms: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onForwardSms: (IncomingSms) -> Unit,
) {
    if (!hasReadSmsPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "READ_SMS permission required",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Grant READ_SMS to browse recent device SMS and manually queue selected messages for forwarding.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                modifier = Modifier.padding(top = 16.dp),
                onClick = onRequestSmsPermissions,
            ) {
                Text("Grant READ_SMS permission")
            }
        }
        return
    }

    LaunchedEffect(Unit) {
        onLoadDeviceSms()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search by sender") },
            placeholder = { Text("VK-HDFCBK or +1234567890") },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "Clear sender search",
                        )
                    }
                }
            },
            singleLine = true,
        )

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = if (searchQuery.isBlank()) "No device SMS found" else "No matching senders",
                    body =
                        if (searchQuery.isBlank()) {
                            "Recent SMS from the device inbox will appear here once READ_SMS is granted."
                        } else {
                            "No inbox messages matched the current sender search."
                        },
                )
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = messages,
                key = { message ->
                    "${message.receivedAtEpochMs}-${message.senderNormalized}-${message.body.hashCode()}"
                },
            ) { message ->
                Card {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = message.senderRaw,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        overlineContent = {
                            Text(formatTimestamp(message.receivedAtEpochMs))
                        },
                        supportingContent = {
                            Text(
                                text = message.body,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { onForwardSms(message) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Queue message for forwarding",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
