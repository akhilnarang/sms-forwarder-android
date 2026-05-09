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

    suspend fun swapPriorityWithNeighbor(rule: ForwardingRuleEntity, direction: Int) {
        require(direction == -1 || direction == 1) { "direction must be -1 or 1" }
        val all = ruleDao.getEnabledRules()
        val currentIndex = all.indexOfFirst { it.id == rule.id }
        if (currentIndex < 0) return
        val neighborIndex = currentIndex + direction
        if (neighborIndex !in all.indices) return
        val neighbor = all[neighborIndex]
        if (neighbor.priority == rule.priority) {
            // Both have same priority due to historical tie; bump rule to break it.
            ruleDao.update(rule.copy(priority = rule.priority + direction))
            return
        }
        ruleDao.update(rule.copy(priority = neighbor.priority))
        ruleDao.update(neighbor.copy(priority = rule.priority))
    }

    suspend fun setEnabled(rule: ForwardingRuleEntity, isEnabled: Boolean) {
        ruleDao.update(rule.copy(enabled = isEnabled))
    }

    suspend fun deleteRule(rule: ForwardingRuleEntity) {
        ruleDao.delete(rule)
    }
}
