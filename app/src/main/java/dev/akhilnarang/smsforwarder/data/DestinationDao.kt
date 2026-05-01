package dev.akhilnarang.smsforwarder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DestinationDao {
    @Query("SELECT * FROM destinations")
    fun getAll(): Flow<List<DestinationEntity>>

    @Query("SELECT * FROM destinations WHERE id = :id")
    suspend fun getById(id: Long): DestinationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(destination: DestinationEntity): Long

    @Update
    suspend fun update(destination: DestinationEntity)

    @Delete
    suspend fun delete(destination: DestinationEntity)
}
