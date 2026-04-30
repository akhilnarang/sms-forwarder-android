package dev.akhilnarang.smsforwarder.data

enum class DeliveryStatus {
    IGNORED,
    PENDING,
    RETRYING,
    SENDING,
    SENT,
    FAILED,
}
