package dev.akhilnarang.smsforwarder.sms

import dev.akhilnarang.smsforwarder.data.ConfiguredSenderRepository
import dev.akhilnarang.smsforwarder.data.ForwardRecordRepository
import dev.akhilnarang.smsforwarder.network.ForwardPayloadFactory
import dev.akhilnarang.smsforwarder.work.ForwardWorkScheduler

class SmsProcessor(
    private val senderRepository: ConfiguredSenderRepository,
    private val forwardRecordRepository: ForwardRecordRepository,
    private val payloadFactory: ForwardPayloadFactory,
    private val workScheduler: ForwardWorkScheduler,
) {
    suspend fun handleIncomingSms(incomingSms: IncomingSms) {
        val matchedSender =
            senderRepository.getMatchingEnabledSender(incomingSms.senderNormalized)
        val payloadJson = payloadFactory.createJson(incomingSms)
        val recordId =
            forwardRecordRepository.insertIncoming(
                incomingSms = incomingSms,
                matchedSender = matchedSender,
                payloadJson = payloadJson,
            )

        if (matchedSender != null) {
            workScheduler.enqueue(recordId)
        }
    }
}
