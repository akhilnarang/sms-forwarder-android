package dev.akhilnarang.smsforwarder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfiguredSenderDao {
    @Query("SELECT * FROM configured_senders ORDER BY enabled DESC, label ASC, rawSender ASC")
    fun observeAll(): Flow<List<ConfiguredSenderEntity>>

    @Query("SELECT * FROM configured_senders WHERE enabled = 1")
    suspend fun getEnabledSenders(): List<ConfiguredSenderEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ConfiguredSenderEntity): Long

    @Update
    suspend fun update(entity: ConfiguredSenderEntity)

    @Query("DELETE FROM configured_senders WHERE id = :id")
    suspend fun deleteById(id: Long)
}

