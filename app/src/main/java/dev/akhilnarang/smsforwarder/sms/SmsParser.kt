package dev.akhilnarang.smsforwarder.sms

import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager

object SmsParser {
    fun parse(intent: Intent): IncomingSms? {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return null
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
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
