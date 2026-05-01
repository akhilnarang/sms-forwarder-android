package dev.akhilnarang.smsforwarder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.akhilnarang.smsforwarder.data.DestinationEntity
import dev.akhilnarang.smsforwarder.data.DestinationType
import org.json.JSONObject

@Composable
internal fun DestinationsTab(
    destinations: List<DestinationEntity>,
    onAddDestination: (String, DestinationType, String, String, String, String, String) -> Unit,
    onSetDestinationEnabled: (DestinationEntity, Boolean) -> Unit,
    onDeleteDestination: (Long) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (destinations.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Add,
                title = "No destinations configured",
                body = "Tap the + button to add a Webhook or Telegram Bot.",
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                destinations.forEach { dest ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                                onDeleteDestination(dest.id)
                                true
                            } else false
                        }
                    )
                    
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 4.dp)
                                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        },
                        content = {
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                ListItem(
                                    headlineContent = { Text(dest.label, fontWeight = FontWeight.SemiBold) },
                                    overlineContent = { Text(dest.type.name) },
                                    supportingContent = { Text(dest.endpointUrl) },
                                    trailingContent = {
                                        Switch(
                                            checked = dest.enabled,
                                            onCheckedChange = { onSetDestinationEnabled(dest, it) }
                                        )
                                    }
                                )
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add Destination")
        }
    }

    if (showAddDialog) {
        AddDestinationDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { label, type, url, authName, authVal, payload, config ->
                onAddDestination(label, type, url, authName, authVal, payload, config)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDestinationDialog(
    onDismiss: () -> Unit,
    onAdd: (String, DestinationType, String, String, String, String, String) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(DestinationType.CUSTOM_WEBHOOK) }
    var endpointUrl by remember { mutableStateOf("") }
    var authName by remember { mutableStateOf("") }
    var authVal by remember { mutableStateOf("") }
    var payloadTemplate by remember { mutableStateOf("") }
    
    var botToken by remember { mutableStateOf("") }
    var chatId by remember { mutableStateOf("") }

    var expanded by remember { mutableStateOf(false) }

    var isJsonValid by remember { mutableStateOf(true) }
    LaunchedEffect(payloadTemplate) {
        if (payloadTemplate.isBlank()) {
            isJsonValid = true
        } else {
            isJsonValid = try {
                JSONObject(payloadTemplate)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Destination") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (e.g. My Server)") },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = type.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DestinationType.entries.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.name) },
                                onClick = {
                                    type = opt
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                if (type == DestinationType.CUSTOM_WEBHOOK) {
                    OutlinedTextField(
                        value = endpointUrl,
                        onValueChange = { endpointUrl = it },
                        label = { Text("Endpoint URL") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    OutlinedTextField(
                        value = authName,
                        onValueChange = { authName = it },
                        label = { Text("Auth Header Name (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = authVal,
                        onValueChange = { authVal = it },
                        label = { Text("Auth Header Value (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = payloadTemplate,
                        onValueChange = { payloadTemplate = it },
                        label = { Text("JSON Payload Template (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 10,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        visualTransformation = JsonSyntaxVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None
                        ),
                        supportingText = { 
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (payloadTemplate.isNotBlank()) {
                                    if (isJsonValid) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = "Valid JSON", modifier = Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                                        Text("Valid JSON format", color = Color(0xFF4CAF50))
                                    } else {
                                        Icon(Icons.Rounded.ErrorOutline, contentDescription = "Invalid JSON", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                        Text("Invalid JSON format", color = MaterialTheme.colorScheme.error)
                                    }
                                } else {
                                    Text("Use {{sender}}, {{body}}, {{receivedAt}}")
                                }
                            }
                        }
                    )
                } else {
                    OutlinedTextField(
                        value = botToken,
                        onValueChange = { botToken = it },
                        label = { Text("Bot Token") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = chatId,
                        onValueChange = { chatId = it },
                        label = { Text("Chat ID") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(label, type, endpointUrl, authName, authVal, payloadTemplate, "{\"botToken\":\"$botToken\",\"chatId\":\"$chatId\"}")
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

class JsonSyntaxVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val rawStr = text.text
        val annotatedString = buildAnnotatedString {
            append(rawStr)
            
            // 1. Highlight JSON Keys (e.g. "myKey":) -> Purple
            val keyPattern = "\"([^\"]+)\"\\s*:".toRegex()
            keyPattern.findAll(rawStr).forEach { match ->
                // Highlight just the quoted string part, not the colon
                val colonIdx = match.value.indexOf(":")
                addStyle(SpanStyle(color = Color(0xFF9C27B0), fontWeight = FontWeight.SemiBold), match.range.first, match.range.first + colonIdx)
            }

            // 2. Highlight placeholders (e.g. {{body}}) -> Bright Orange
            val placeholderPattern = "\\{\\{.*?\\}\\}".toRegex()
            placeholderPattern.findAll(rawStr).forEach { match ->
                addStyle(SpanStyle(color = Color(0xFFFF9800), fontWeight = FontWeight.ExtraBold), match.range.first, match.range.last + 1)
            }

            // 3. Highlight string values -> Green
            val stringValPattern = ":\\s*\"([^\"]*)\"".toRegex()
            stringValPattern.findAll(rawStr).forEach { match ->
                val firstQuote = match.value.indexOf("\"")
                addStyle(SpanStyle(color = Color(0xFF4CAF50)), match.range.first + firstQuote, match.range.last + 1)
            }
            
            // 4. Highlight booleans & null -> Blue
            val keywordPattern = ":\\s*(true|false|null)\\b".toRegex()
            keywordPattern.findAll(rawStr).forEach { match ->
                val keywordStart = match.range.first + match.value.indexOf(match.groups[1]!!.value)
                addStyle(SpanStyle(color = Color(0xFF2196F3), fontWeight = FontWeight.Bold), keywordStart, keywordStart + match.groups[1]!!.value.length)
            }
        }
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}
