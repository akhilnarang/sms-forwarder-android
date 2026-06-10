package dev.akhilnarang.smsforwarder.sms

import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager

object SmsParser {
    fun parse(intent: Intent): IncomingSms? {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return null
        }

        // A malformed intent/PDU can make this return null, return an empty array, or
        // contain null elements. Parse defensively so a bad PDU yields null (skip)
        // rather than crashing onReceive (which runs before goAsync and drops the broadcast).
        val messages =
            try {
                Telephony.Sms.Intents.getMessagesFromIntent(intent)
            } catch (_: Exception) {
                null
            }
                ?.filterNotNull()
                ?: return null
        if (messages.isEmpty()) {
            return null
        }

        val senderRaw = messages.first().originatingAddress?.trim().orEmpty()
        if (senderRaw.isBlank()) {
            return null
        }
        val messageBody = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val timestamp = messages.first().timestampMillis
        val subscriptionId =
            intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, INVALID_SUBSCRIPTION_ID)
                .takeIf { it != INVALID_SUBSCRIPTION_ID }

        return IncomingSms(
            senderRaw = senderRaw,
            senderNormalized = normalizeSender(senderRaw),
            body = messageBody,
            receivedAtEpochMs = timestamp,
            subscriptionId = subscriptionId,
            multipart = messages.size > 1,
        )
    }

    private const val INVALID_SUBSCRIPTION_ID = -1
}
