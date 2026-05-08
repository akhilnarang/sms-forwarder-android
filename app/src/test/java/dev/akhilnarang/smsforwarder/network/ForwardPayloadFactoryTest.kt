package dev.akhilnarang.smsforwarder.network

import dev.akhilnarang.smsforwarder.sms.IncomingSms
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ForwardPayloadFactoryTest {
    private val factory = ForwardPayloadFactory()
    private val json = Json { ignoreUnknownKeys = true }

    private fun makeSms(
        sender: String = "VK-HDFCBK",
        body: String = "Your OTP is 123456",
        epochMs: Long = 1_700_000_000_000L,
    ) = IncomingSms(
        senderRaw = sender,
        senderNormalized = sender.uppercase(),
        body = body,
        receivedAtEpochMs = epochMs,
        subscriptionId = 1,
        multipart = false,
    )

    @Test
    fun createJson_maps_sms_fields_into_payload() {
        val payload = json.decodeFromString<ForwardPayload>(factory.createJson(makeSms()))
        assertEquals("VK-HDFCBK", payload.sender)
        assertEquals("Your OTP is 123456", payload.body)
    }

    @Test
    fun createJson_emits_only_backend_fields() {
        val payloadJson = factory.createJson(makeSms())
        val keys = json.parseToJsonElement(payloadJson).jsonObject.keys

        assertEquals(
            setOf("sender", "body", "received_at"),
            keys,
        )
    }

    @Test
    fun `createTelegramText escapes html-special characters in body`() {
        val sms = makeSms(
            sender = "VK-HDFCBK",
            body = "Your OTP is 123 <3 use within 10 mins",
        )

        val result = factory.createTelegramText(
            template = "<b>From:</b> {{sender}}\n\n{{body}}",
            incomingSms = sms,
            customKeysMap = emptyMap(),
        )

        assertEquals("<b>From:</b> VK-HDFCBK\n\nYour OTP is 123 &lt;3 use within 10 mins", result)
    }

    @Test
    fun `createTelegramText escapes ampersand and gt in custom key values`() {
        val sms = makeSms(sender = "S", body = "body", epochMs = 0L)

        val result = factory.createTelegramText(
            template = "{{merchant}}",
            incomingSms = sms,
            customKeysMap = mapOf("merchant" to "Tom & Jerry > Cat"),
        )

        assertEquals("Tom &amp; Jerry &gt; Cat", result)
    }
}
