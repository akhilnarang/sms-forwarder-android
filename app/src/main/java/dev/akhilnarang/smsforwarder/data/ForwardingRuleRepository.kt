package dev.akhilnarang.smsforwarder.data

import kotlinx.coroutines.flow.Flow

class ForwardingRuleRepository(private val ruleDao: ForwardingRuleDao) {
    fun getAllRules(): Flow<List<ForwardingRuleEntity>> = ruleDao.getAll()

    suspend fun getEnabledRules(): List<ForwardingRuleEntity> = ruleDao.getEnabledRules()

    suspend fun addRule(rule: ForwardingRuleEntity): Long {
        val currentMax = ruleDao.getMaxPriority() ?: 0
        val ruleWithPriority = rule.copy(priority = currentMax + 1)
        return ruleDao.insert(ruleWithPriority)
    }
    
    suspend fun updateRule(rule: ForwardingRuleEntity) {
        ruleDao.insert(rule) // using replace strategy
    }

    suspend fun updateRulePriority(rule: ForwardingRuleEntity, newPriority: Int) {
        ruleDao.update(rule.copy(priority = newPriority))
    }

    suspend fun setEnabled(rule: ForwardingRuleEntity, isEnabled: Boolean) {
        ruleDao.update(rule.copy(enabled = isEnabled))
    }

    suspend fun deleteRule(rule: ForwardingRuleEntity) {
        ruleDao.delete(rule)
    }
}
