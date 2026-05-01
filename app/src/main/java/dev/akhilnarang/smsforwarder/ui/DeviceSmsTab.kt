package dev.akhilnarang.smsforwarder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akhilnarang.smsforwarder.data.DestinationEntity
import dev.akhilnarang.smsforwarder.sms.IncomingSms

@Composable
internal fun DeviceSmsTab(
    hasReadSmsPermission: Boolean,
    messages: List<IncomingSms>,
    rules: List<dev.akhilnarang.smsforwarder.data.ForwardingRuleEntity>,
    destinations: List<DestinationEntity>,
    searchQuery: String,
    onRequestSmsPermissions: () -> Unit,
    onLoadDeviceSms: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onForwardSms: (IncomingSms, dev.akhilnarang.smsforwarder.data.ForwardingRuleEntity) -> Unit,
) {
    var smsToForward by remember { mutableStateOf<IncomingSms?>(null) }

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
        } else {
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
                                IconButton(onClick = { smsToForward = message }) {
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

    smsToForward?.let { message ->
        AlertDialog(
            onDismissRequest = { smsToForward = null },
            title = { Text("Select Rule for Forwarding") },
            text = {
                if (rules.isEmpty()) {
                    Text("No rules configured. Please add a rule first.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(rules) { rule ->
                            val dest = destinations.find { it.id == rule.destinationId }
                            val patternStr = Regex.escape(rule.senderPattern).replace("\\*", ".*")
                            val regex = Regex(patternStr, RegexOption.IGNORE_CASE)
                            val isMatch = regex.matches(message.senderNormalized) && 
                                (rule.bodyContains.isNullOrEmpty() || message.body.contains(rule.bodyContains, ignoreCase = true))

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onForwardSms(message, rule)
                                        smsToForward = null
                                    },
                                shape = MaterialTheme.shapes.small,
                                color = if (isMatch) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = rule.label + if (isMatch) " (Auto-Matched)" else "", 
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isMatch) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = dest?.label ?: "Unknown Destination", 
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isMatch) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { smsToForward = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
