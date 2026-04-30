package dev.akhilnarang.smsforwarder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.akhilnarang.smsforwarder.data.ConfiguredSenderEntity

@Composable
internal fun SendersTab(
    senders: List<ConfiguredSenderEntity>,
    onAddSender: (String, String) -> Unit,
    onSetSenderEnabled: (ConfiguredSenderEntity, Boolean) -> Unit,
    onDeleteSender: (Long) -> Unit,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var rawSender by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Allowed senders",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Label") },
                    placeholder = { Text("Bank OTP") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = rawSender,
                    onValueChange = { rawSender = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Sender") },
                    placeholder = { Text("VK-HDFCBK or +1234567890") },
                    singleLine = true,
                )
                Button(
                    onClick = { onAddSender(label, rawSender) },
                ) {
                    Text("Add sender")
                }
            }
        }

        if (senders.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.PersonOff,
                title = "No senders configured yet",
                body = "Messages from unknown senders will still appear in the queue as ignored.",
            )
        } else {
            senders.forEach { sender ->
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = sender.label,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            overlineContent = { Text(sender.rawSender) },
                            supportingContent = {
                                Text("Normalized: ${sender.normalizedSender}")
                            },
                            trailingContent = {
                                Switch(
                                    checked = sender.enabled,
                                    onCheckedChange = { enabled ->
                                        onSetSenderEnabled(sender, enabled)
                                    },
                                )
                            },
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { onDeleteSender(sender.id) }) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}
