package dev.akhilnarang.smsforwarder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akhilnarang.smsforwarder.data.DeliveryStatus
import dev.akhilnarang.smsforwarder.data.ForwardRecordEntity

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QueueTab(
    records: List<ForwardRecordEntity>,
    onRetryRecord: (ForwardRecordEntity) -> Unit,
    onOpenRecord: (ForwardRecordEntity) -> Unit,
    onClearQueue: () -> Unit,
) {
    if (records.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.Inbox,
            title = "No messages captured yet",
            body = "Matching SMS and validation test records will appear here with delivery status and retry controls.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClearQueue) {
                    Text("Clear Queue")
                }
            }
        }
        items(records, key = { it.id }) { record ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenRecord(record) },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ListItem(
                        modifier = Modifier.fillMaxWidth(),
                        overlineContent = {
                            Text(
                                text = formatTimestamp(record.receivedAtEpochMs),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        headlineContent = {
                            Text(
                                text = record.senderRaw.ifBlank { "(Unknown sender)" },
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = record.messageBody,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = record.statusReason,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                record.responseDetails?.let { responseDetails ->
                                    Text(
                                        text = if (record.status == dev.akhilnarang.smsforwarder.data.DeliveryStatus.FAILED) "Error: $responseDetails" else "Response: $responseDetails",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (record.status == dev.akhilnarang.smsforwarder.data.DeliveryStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (record.isTestRecord) {
                                    TestBadge()
                                }
                                StatusChip(status = record.status)
                            }
                        },
                    )
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (
                            record.status == DeliveryStatus.FAILED ||
                            record.status == DeliveryStatus.PENDING ||
                            record.status == DeliveryStatus.RETRYING
                        ) {
                            Button(onClick = { onRetryRecord(record) }) {
                                Text("Retry now")
                            }
                        }
                        TextButton(onClick = { onOpenRecord(record) }) {
                            Text("View details")
                        }
                    }
                }
            }
        }
    }
}
