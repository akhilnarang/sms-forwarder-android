package dev.akhilnarang.smsforwarder.network

import dev.akhilnarang.smsforwarder.sms.IncomingSms
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
}
