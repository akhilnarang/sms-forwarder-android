package dev.akhilnarang.smsforwarder.network

import dev.akhilnarang.smsforwarder.sms.IncomingSms
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.time.Instant

@Serializable
data class ForwardPayload(
    val sender: String,
    val body: String,
    @SerialName("received_at")
    val receivedAt: String
)

class ForwardPayloadFactory {
    private val json = Json { encodeDefaults = true }

    fun createJson(incomingSms: IncomingSms, customKeysMap: Map<String, String> = emptyMap()): String {
        val baseString = json.encodeToString(
            ForwardPayload(
                sender = incomingSms.senderRaw,
                body = incomingSms.body,
                receivedAt = Instant.ofEpochMilli(incomingSms.receivedAtEpochMs).toString()
            ),
        )
        if (customKeysMap.isEmpty()) return baseString

        val obj = JSONObject(baseString)
        for ((key, value) in customKeysMap) {
            obj.put(key, value)
        }
        return obj.toString()
    }
        
    fun createCustomJson(template: String, incomingSms: IncomingSms, customKeysMap: Map<String, String>): String {
        val receivedAtIso = Instant.ofEpochMilli(incomingSms.receivedAtEpochMs).toString()
        val replacements = mutableMapOf(
            "sender" to JSONObject.quote(incomingSms.senderRaw).removeSurrounding("\""),
            "body" to JSONObject.quote(incomingSms.body).removeSurrounding("\""),
            "received_at" to receivedAtIso
        )
        for ((key, value) in customKeysMap) {
            replacements[key] = JSONObject.quote(value).removeSurrounding("\"")
        }

        val regex = Regex("\\{\\{([^{}]+)\\}\\}")
        val replacedTemplate = regex.replace(template) { matchResult ->
            val key = matchResult.groupValues[1]
            replacements[key] ?: matchResult.value
        }
        
        if (customKeysMap.isEmpty()) return replacedTemplate

        return try {
            val obj = JSONObject(replacedTemplate)
            for ((key, value) in customKeysMap) {
                // If it's not already in the template, append it
                if (!obj.has(key)) {
                    // Try to parse the value as JSON (e.g. number/boolean) or fallback to string
                    try {
                        val parsedVal = org.json.JSONTokener(value).nextValue()
                        obj.put(key, parsedVal)
                    } catch (e: Exception) {
                        obj.put(key, value)
                    }
                }
            }
            obj.toString(2)
        } catch (e: Exception) {
            // If template isn't valid JSON (e.g. malformed), just return the replaced string
            replacedTemplate
        }
    }

    fun createTextTemplate(template: String, incomingSms: IncomingSms, customKeysMap: Map<String, String>): String {
        val receivedAtIso = Instant.ofEpochMilli(incomingSms.receivedAtEpochMs).toString()
        val replacements = mutableMapOf(
            "sender" to incomingSms.senderRaw,
            "body" to incomingSms.body,
            "received_at" to receivedAtIso
        )
        for ((key, value) in customKeysMap) {
            replacements[key] = value
        }

        val regex = Regex("\\{\\{([^{}]+)\\}\\}")
        return regex.replace(template) { matchResult ->
            val key = matchResult.groupValues[1]
            replacements[key] ?: matchResult.value
        }
    }

    fun createTelegramText(template: String, incomingSms: IncomingSms, customKeysMap: Map<String, String>): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val receivedAtIso = Instant.ofEpochMilli(incomingSms.receivedAtEpochMs).toString()
        val replacements = mutableMapOf(
            "sender" to esc(incomingSms.senderRaw),
            "body" to esc(incomingSms.body),
            "received_at" to receivedAtIso
        )
        for ((key, value) in customKeysMap) {
            replacements[key] = esc(value)
        }

        val regex = Regex("\\{\\{([^{}]+)\\}\\}")
        return regex.replace(template) { matchResult ->
            val key = matchResult.groupValues[1]
            replacements[key] ?: matchResult.value
        }
    }
}
