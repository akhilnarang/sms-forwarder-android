package dev.akhilnarang.smsforwarder.sms

import android.util.Log
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
            val regex = wildcardRegex(rule.senderPattern)

            if (regex.matches(incomingSms.senderNormalized)) {
                val isBodyMatch = if (rule.bodyContains.isNullOrEmpty()) {
                    true
                } else {
                    wildcardRegex(rule.bodyContains).containsMatchIn(incomingSms.body)
                }

                if (isBodyMatch) {
                    matchedRule = rule
                    destinationId = rule.destinationId
                    
                    var customKeysMap: Map<String, String> = emptyMap()
                    rule.customPayloadKeys?.let {
                        try {
                            val json = JSONObject(it)
                            val map = mutableMapOf<String, String>()
                            json.keys().forEach { key -> map[key] = json.getString(key) }
                            customKeysMap = map
                        } catch (e: Exception) {
                            Log.w("SmsProcessor", "Failed to parse customPayloadKeys for rule ${rule.id}", e)
                        }
                    }
                    
                    // Retrieve destination to check for custom payload template
                    val destination = destinationDao.getById(rule.destinationId)
                    if (destination != null) {
                        if (destination.type == dev.akhilnarang.smsforwarder.data.DestinationType.TELEGRAM_PRESET) {
                            try {
                                val config = JSONObject(destination.configJson ?: "{}")
                                val chatId = config.optString("chatId", "")
                                val template = if (!destination.payloadTemplate.isNullOrBlank()) destination.payloadTemplate else "<b>From:</b> {{sender}}\n\n{{body}}"
                                val textStr = payloadFactory.createTelegramText(template, incomingSms, customKeysMap)
                                val json = JSONObject()
                                json.put("chat_id", chatId)
                                json.put("text", textStr)
                                json.put("parse_mode", "HTML")
                                payloadJson = json.toString()
                            } catch (e: Exception) {
                                Log.w("SmsProcessor", "Failed to build Telegram payload for rule ${rule.id}", e)
                                val failedId =
                                    forwardRecordRepository.insertIncoming(
                                        incomingSms = incomingSms,
                                        matchedRule = rule,
                                        destinationId = destinationId,
                                        payloadJson = "",
                                    )
                                forwardRecordRepository.markFailed(
                                    failedId,
                                    "Telegram payload build failed: ${e.message}",
                                )
                                return
                            }
                        } else if (!destination.payloadTemplate.isNullOrBlank()) {
                            payloadJson = payloadFactory.createCustomJson(destination.payloadTemplate, incomingSms, customKeysMap)
                        } else {
                            payloadJson = payloadFactory.createJson(incomingSms, customKeysMap)
                        }
                    } else {
                        payloadJson = payloadFactory.createJson(incomingSms, customKeysMap)
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

internal fun wildcardRegex(pattern: String): Regex {
    // Split on '*' first so each literal segment is properly escaped, then
    // join with '.*'. Doing Regex.escape on the whole pattern doesn't work
    // because Pattern.quote wraps the input in \Q...\E, so '*' never gets
    // converted to a wildcard.
    val regexPattern = pattern.split("*").joinToString(".*") {
        if (it.isEmpty()) "" else Regex.escape(it)
    }
    return Regex(regexPattern, RegexOption.IGNORE_CASE)
}
