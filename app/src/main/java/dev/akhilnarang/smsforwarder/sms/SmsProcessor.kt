package dev.akhilnarang.smsforwarder.sms

import dev.akhilnarang.smsforwarder.data.DestinationDao
import dev.akhilnarang.smsforwarder.data.ForwardingRuleDao
import dev.akhilnarang.smsforwarder.data.ForwardingRuleEntity
import dev.akhilnarang.smsforwarder.data.ForwardRecordRepository
import dev.akhilnarang.smsforwarder.network.ForwardPayloadFactory
import dev.akhilnarang.smsforwarder.work.ForwardWorkScheduler
import org.json.JSONObject

class SmsProcessor(
    private val ruleDao: ForwardingRuleDao,
    private val destinationDao: DestinationDao,
    private val forwardRecordRepository: ForwardRecordRepository,
    private val payloadFactory: ForwardPayloadFactory,
    private val workScheduler: ForwardWorkScheduler,
) {
    suspend fun handleIncomingSms(incomingSms: IncomingSms) {
        val rules = ruleDao.getEnabledRules()
        var matchedRule: ForwardingRuleEntity? = null
        var destinationId: Long? = null
        var payloadJson = payloadFactory.createJson(incomingSms) // Default payload

        for (rule in rules) {
            val patternStr = Regex.escape(rule.senderPattern).replace("\\*", ".*")
            val regex = Regex(patternStr, RegexOption.IGNORE_CASE)
            
            if (regex.matches(incomingSms.senderNormalized)) {
                if (rule.bodyContains.isNullOrEmpty() || incomingSms.body.contains(rule.bodyContains, ignoreCase = true)) {
                    matchedRule = rule
                    destinationId = rule.destinationId
                    
                    // Retrieve destination to check for custom payload template
                    val destination = destinationDao.getById(rule.destinationId)
                    if (destination != null) {
                        if (destination.type == dev.akhilnarang.smsforwarder.data.DestinationType.TELEGRAM_PRESET) {
                            try {
                                val config = JSONObject(destination.configJson ?: "{}")
                                val chatId = config.optString("chatId", "")
                                val template = if (!destination.payloadTemplate.isNullOrBlank()) destination.payloadTemplate else "<b>From:</b> {{sender}}\n\n{{body}}"
                                val textStr = payloadFactory.createTextTemplate(template, incomingSms, emptyMap())
                                val json = JSONObject()
                                json.put("chat_id", chatId)
                                json.put("text", textStr)
                                json.put("parse_mode", "HTML")
                                payloadJson = json.toString()
                            } catch (e: Exception) { }
                        } else if (!destination.payloadTemplate.isNullOrBlank()) {
                            var customKeysMap: Map<String, String> = emptyMap()
                            rule.customPayloadKeys?.let {
                                try {
                                    val json = JSONObject(it)
                                    val map = mutableMapOf<String, String>()
                                    json.keys().forEach { key -> map[key] = json.getString(key) }
                                    customKeysMap = map
                                } catch (e: Exception) {}
                            }
                            payloadJson = payloadFactory.createCustomJson(destination.payloadTemplate, incomingSms, customKeysMap)
                        }
                    }
                    break
                }
            }
        }

        val recordId =
            forwardRecordRepository.insertIncoming(
                incomingSms = incomingSms,
                matchedRule = matchedRule,
                destinationId = destinationId,
                payloadJson = payloadJson,
            )

        if (matchedRule != null) {
            workScheduler.enqueue(recordId)
        }
    }
}
