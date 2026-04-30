package dev.akhilnarang.smsforwarder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.akhilnarang.smsforwarder.data.ForwardSummary

@Composable
internal fun SummaryTab(summary: ForwardSummary) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryCard("Received", summary.totalCount)
        SummaryCard("Matched", summary.matchedCount)
        SummaryCard("Pending", summary.pendingCount)
        SummaryCard("Sent", summary.sentCount)
        SummaryCard("Failed", summary.failedCount)
        SummaryCard("Ignored", summary.ignoredCount)
    }
}

@Composable
internal fun SummaryCard(
    label: String,
    count: Int,
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}
