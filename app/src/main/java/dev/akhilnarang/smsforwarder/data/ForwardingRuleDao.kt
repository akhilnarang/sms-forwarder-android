package dev.akhilnarang.smsforwarder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ForwardingRuleDao {
    @Query("SELECT * FROM forwarding_rules ORDER BY priority ASC")
    fun getAll(): Flow<List<ForwardingRuleEntity>>

    @Query("SELECT * FROM forwarding_rules WHERE enabled = 1 ORDER BY priority ASC")
    suspend fun getEnabledRules(): List<ForwardingRuleEntity>

    @Query("SELECT MAX(priority) FROM forwarding_rules")
    suspend fun getMaxPriority(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: ForwardingRuleEntity): Long

    @Update
    suspend fun update(rule: ForwardingRuleEntity)

    @Delete
    suspend fun delete(rule: ForwardingRuleEntity)
}
