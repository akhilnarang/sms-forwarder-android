package dev.akhilnarang.smsforwarder.sms

import dev.akhilnarang.smsforwarder.data.DestinationDao
import dev.akhilnarang.smsforwarder.data.ForwardingRuleDao
import dev.akhilnarang.smsforwarder.data.ForwardingRuleEntity
import dev.akhilnarang.smsforwarder.data.ForwardRecordRepository
import dev.akhilnarang.smsforwarder.network.ForwardPayloadFactory
import dev.akhilnarang.smsforwarder.work.ForwardWorkScheduler

class SmsProcessor(
    private val ruleDao: ForwardingRuleDao,
    private val forwardRecordRepository: ForwardRecordRepository,
    private val payloadFactory: ForwardPayloadFactory,
    private val workScheduler: ForwardWorkScheduler,
) {
    suspend fun handleIncomingSms(incomingSms: IncomingSms) {
        val rules = ruleDao.getEnabledRules()
        var matchedRule: ForwardingRuleEntity? = null

        for (rule in rules) {
            val patternStr = Regex.escape(rule.senderPattern).replace("\\*", ".*")
            val regex = Regex(patternStr, RegexOption.IGNORE_CASE)
            
            if (regex.matches(incomingSms.senderNormalized)) {
                if (rule.bodyContains.isNullOrEmpty() || incomingSms.body.contains(rule.bodyContains, ignoreCase = true)) {
                    matchedRule = rule
                    break
                }
            }
        }

        val payloadJson = payloadFactory.createJson(incomingSms)
        val recordId =
            forwardRecordRepository.insertIncoming(
                incomingSms = incomingSms,
                matchedRule = matchedRule,
                payloadJson = payloadJson,
            )

        if (matchedRule != null) {
            workScheduler.enqueue(recordId)
        }
    }
}
