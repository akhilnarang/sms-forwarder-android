package dev.akhilnarang.smsforwarder.network

import dev.akhilnarang.smsforwarder.sms.IncomingSms
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

@Serializable
data class ForwardPayload(
    val sender: String,
    val body: String,
)

class ForwardPayloadFactory {
    private val json = Json { encodeDefaults = true }

    fun createJson(incomingSms: IncomingSms): String =
        json.encodeToString(
            ForwardPayload(
                sender = incomingSms.senderRaw,
                body = incomingSms.body,
            ),
        )
        
    fun createCustomJson(template: String, incomingSms: IncomingSms, customKeysMap: Map<String, String>): String {
        val replacements = mutableMapOf(
            "sender" to JSONObject.quote(incomingSms.senderRaw).removeSurrounding("\""),
            "body" to JSONObject.quote(incomingSms.body).removeSurrounding("\""),
            "receivedAt" to incomingSms.receivedAtEpochMs.toString()
        )
        for ((key, value) in customKeysMap) {
            replacements[key] = JSONObject.quote(value).removeSurrounding("\"")
        }

        val regex = Regex("\\{\\{([^{}]+)\\}\\}")
        return regex.replace(template) { matchResult ->
            val key = matchResult.groupValues[1]
            replacements[key] ?: matchResult.value
        }
    }

    fun createTextTemplate(template: String, incomingSms: IncomingSms, customKeysMap: Map<String, String>): String {
        val replacements = mutableMapOf(
            "sender" to incomingSms.senderRaw,
            "body" to incomingSms.body,
            "receivedAt" to incomingSms.receivedAtEpochMs.toString()
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
}
