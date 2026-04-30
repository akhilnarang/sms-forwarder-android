package dev.akhilnarang.smsforwarder.sms

data class IncomingSms(
    val senderRaw: String,
    val senderNormalized: String,
    val body: String,
    val receivedAtEpochMs: Long,
    val subscriptionId: Int?,
    val multipart: Boolean,
)
