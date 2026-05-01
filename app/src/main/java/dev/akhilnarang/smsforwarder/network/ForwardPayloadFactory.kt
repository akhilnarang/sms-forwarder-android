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
        var result = template
        result = result.replace("{{sender}}", JSONObject.quote(incomingSms.senderRaw).removeSurrounding("\""))
        result = result.replace("{{body}}", JSONObject.quote(incomingSms.body).removeSurrounding("\""))
        result = result.replace("{{receivedAt}}", incomingSms.receivedAtEpochMs.toString())
        for ((key, value) in customKeysMap) {
            result = result.replace("{{" + key + "}}", JSONObject.quote(value).removeSurrounding("\""))
        }
        return result
    }
}
