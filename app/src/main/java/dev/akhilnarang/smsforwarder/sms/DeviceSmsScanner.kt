package dev.akhilnarang.smsforwarder.sms

import android.content.Context
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceSmsScanner(
    private val context: Context,
) {
    suspend fun getRecentSms(limit: Int = DEFAULT_LIMIT): List<IncomingSms> =
        withContext(Dispatchers.IO) {
            val messages = mutableListOf<IncomingSms>()
            val projection =
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.SUBSCRIPTION_ID,
                )

            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT $limit",
            )?.use { cursor ->
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val subIdIdx = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

                while (cursor.moveToNext()) {
                    val rawSender = cursor.getString(addressIdx)?.trim().orEmpty()
                    if (rawSender.isBlank()) {
                        continue
                    }

                    val subscriptionId =
                        if (subIdIdx >= 0) {
                            cursor.getInt(subIdIdx).takeIf { it != INVALID_SUBSCRIPTION_ID }
                        } else {
                            null
                        }

                    messages.add(
                        IncomingSms(
                            senderRaw = rawSender,
                            senderNormalized = normalizeSender(rawSender),
                            body = cursor.getString(bodyIdx).orEmpty(),
                            receivedAtEpochMs = cursor.getLong(dateIdx),
                            subscriptionId = subscriptionId,
                            multipart = false,
                        ),
                    )
                }
            }

            messages
        }

    private companion object {
        const val DEFAULT_LIMIT = 500
        const val INVALID_SUBSCRIPTION_ID = -1
    }
}
