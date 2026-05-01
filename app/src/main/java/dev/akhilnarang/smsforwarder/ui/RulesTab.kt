package dev.akhilnarang.smsforwarder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.akhilnarang.smsforwarder.data.DestinationEntity
import dev.akhilnarang.smsforwarder.data.ForwardingRuleEntity
import org.json.JSONObject

@Composable
internal fun RulesTab(
    rules: List<ForwardingRuleEntity>,
    destinations: List<DestinationEntity>,
    onAddRule: (ForwardingRuleEntity) -> Unit,
    onEditRule: (ForwardingRuleEntity) -> Unit,
    onSetRuleEnabled: (ForwardingRuleEntity, Boolean) -> Unit,
    onDeleteRule: (ForwardingRuleEntity) -> Unit,
    onUpdateRulePriority: (ForwardingRuleEntity, Int) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editRule by remember { mutableStateOf<ForwardingRuleEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (rules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No forwarding rules configured.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 80.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    val dest = destinations.find { it.id == rule.destinationId }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { editRule = rule }
                    ) {
                        ListItem(
                            headlineContent = { Text(rule.label, fontWeight = FontWeight.Bold) },
                            supportingContent = {
                                Column {
                                    Text("Sender: ${rule.senderPattern}")
                                    if (!rule.bodyContains.isNullOrBlank()) {
                                        Text("Body contains: ${rule.bodyContains}")
                                    }
                                    Text("Destination: ${dest?.label ?: "Unknown"}")
                                    Text("Priority: ${rule.priority}", style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onUpdateRulePriority(rule, rule.priority - 1) }) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                                    }
                                    IconButton(onClick = { onUpdateRulePriority(rule, rule.priority + 1) }) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                                    }
                                    Switch(
                                        checked = rule.enabled,
                                        onCheckedChange = { onSetRuleEnabled(rule, it) }
                                    )
                                    IconButton(onClick = { onDeleteRule(rule) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Rule")
        }
    }

    if (showAddDialog) {
        AddRuleDialog(
            destinations = destinations,
            onDismiss = { showAddDialog = false },
            onSave = {
                onAddRule(it)
                showAddDialog = false
            }
        )
    }

    editRule?.let { ruleToEdit ->
        AddRuleDialog(
            initialRule = ruleToEdit,
            destinations = destinations,
            onDismiss = { editRule = null },
            onSave = { updatedRule ->
                onEditRule(updatedRule)
                editRule = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRuleDialog(
    initialRule: ForwardingRuleEntity? = null,
    destinations: List<DestinationEntity>,
    onDismiss: () -> Unit,
    onSave: (ForwardingRuleEntity) -> Unit
) {
    var label by rememberSaveable { mutableStateOf(initialRule?.label ?: "") }
    var senderPattern by rememberSaveable { mutableStateOf(initialRule?.senderPattern ?: "") }
    var bodyContains by rememberSaveable { mutableStateOf(initialRule?.bodyContains ?: "") }
    // Note: DestinationEntity isn't Saveable natively, so keeping remember for selectedDestination
    var selectedDestination by remember { mutableStateOf(destinations.find { it.id == initialRule?.destinationId } ?: destinations.firstOrNull()) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    var customKeysStr by rememberSaveable { mutableStateOf(initialRule?.customPayloadKeys ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (e.g., HDFC Transactions)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = senderPattern,
                    onValueChange = { senderPattern = it },
                    label = { Text("Sender Pattern (* allowed)") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("e.g. *HDFC*") }
                )

                OutlinedTextField(
                    value = bodyContains,
                    onValueChange = { bodyContains = it },
                    label = { Text("Body Contains (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedDestination?.label ?: "Select Destination",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Route to") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        destinations.forEach { dest ->
                            DropdownMenuItem(
                                text = { Text(dest.label) },
                                onClick = {
                                    selectedDestination = dest
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = customKeysStr,
                    onValueChange = { customKeysStr = it },
                    label = { Text("Custom JSON (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("e.g. {\"category\":\"OTP\"}") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedDestination != null) {
                        var parsedJson: String? = null
                        if (customKeysStr.isNotBlank()) {
                            try {
                                parsedJson = JSONObject(customKeysStr).toString()
                            } catch (e: Exception) {
                                // Ignore invalid json for now
                            }
                        }

                        val rule = initialRule?.copy(
                            label = label,
                            senderPattern = senderPattern,
                            bodyContains = bodyContains.ifBlank { null },
                            destinationId = selectedDestination!!.id,
                            customPayloadKeys = parsedJson,
                        ) ?: ForwardingRuleEntity(
                            priority = 0, // Repository assigns actual priority
                            label = label,
                            senderPattern = senderPattern,
                            bodyContains = bodyContains.ifBlank { null },
                            destinationId = selectedDestination!!.id,
                            customPayloadKeys = parsedJson,
                            enabled = true
                        )
                        onSave(rule)
                    }
                },
                enabled = label.isNotBlank() && senderPattern.isNotBlank() && selectedDestination != null
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
