package dev.akhilnarang.smsforwarder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.akhilnarang.smsforwarder.data.DeliveryStatus
import dev.akhilnarang.smsforwarder.data.ForwardRecordEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun StatusChip(status: DeliveryStatus) {
    FilterChip(
        selected = true,
        onClick = {},
        label = { Text(status.name) },
    )
}

@Composable
internal fun RecordDetailDialog(
    record: ForwardRecordEntity,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Text(
                text = "Message #${record.id}",
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (record.isTestRecord) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                        ) {
                            Text(
                                text = "⚠ VALIDATION TEST — this is a synthetic record, not a real SMS.",
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                    Text("Sender: ${record.senderRaw}")
                    Text("Normalized: ${record.senderNormalized}")
                    Text("Received: ${formatTimestamp(record.receivedAtEpochMs)}")
                    Text("Status: ${record.status.name}")
                    Text("Reason: ${record.statusReason}")
                    Text("Attempts: ${record.attemptCount}")
                    record.lastAttemptedAtEpochMs?.let {
                        Text("Last attempt: ${formatTimestamp(it)}")
                    }
                    record.sentAtEpochMs?.let {
                        Text("Sent at: ${formatTimestamp(it)}")
                    }
                    record.lastError?.let {
                        Text(
                            text = "Last error: $it",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    HorizontalDivider()
                    Text(
                        text = "Body",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(record.messageBody)
                    HorizontalDivider()
                    Text(
                        text = "Payload snapshot",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(record.payloadJson)
                }
            }
        },
    )
}

@Composable
internal fun TestBadge() {
    FilterChip(
        selected = false,
        onClick = {},
        label = { Text("TEST") },
    )
}

@Composable
internal fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = body,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun formatTimestamp(epochMillis: Long): String =
    TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

internal val TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
