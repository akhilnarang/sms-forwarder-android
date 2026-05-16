package dev.akhilnarang.smsforwarder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ForwardRecordDao {
    @Insert
    suspend fun insert(entity: ForwardRecordEntity): Long

    @Query("SELECT * FROM forward_records ORDER BY receivedAtEpochMs DESC")
    fun observeAll(): Flow<List<ForwardRecordEntity>>

    @Query("SELECT * FROM forward_records WHERE id = :id")
    suspend fun getById(id: Long): ForwardRecordEntity?

    @Query(
        """
        UPDATE forward_records
        SET status = :status,
            lastError = NULL
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(id: Long, status: DeliveryStatus)

    @Query(
        """
        UPDATE forward_records
        SET status = :status,
            attemptCount = attemptCount + 1,
            lastAttemptedAtEpochMs = :attemptedAtEpochMs,
            lastError = NULL
        WHERE id = :id
          AND status IN ('PENDING', 'FAILED', 'RETRYING')
        """,
    )
    suspend fun markSendingIfEligible(
        id: Long,
        status: DeliveryStatus = DeliveryStatus.SENDING,
        attemptedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE forward_records
        SET status = :status,
            lastError = :error
        WHERE id = :id
        """,
    )
    suspend fun updateStatusWithError(
        id: Long,
        error: String,
        status: DeliveryStatus,
    )

    @Query(
        """
        UPDATE forward_records
        SET status = :status,
            lastError = :responseDetails,
            sentAtEpochMs = :sentAtEpochMs
        WHERE id = :id
        """,
    )
    suspend fun markSent(
        id: Long,
        sentAtEpochMs: Long,
        responseDetails: String?,
        status: DeliveryStatus = DeliveryStatus.SENT,
    )

    @Query("DELETE FROM forward_records")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM forward_records WHERE status = :status")
    suspend fun countByStatus(status: dev.akhilnarang.smsforwarder.data.DeliveryStatus): Int

    @Query(
        """
        SELECT
            COUNT(*) AS totalCount,
            COALESCE(SUM(CASE WHEN status != 'IGNORED' THEN 1 ELSE 0 END), 0) AS matchedCount,
            COALESCE(SUM(CASE WHEN status = 'SENT' THEN 1 ELSE 0 END), 0) AS sentCount,
            COALESCE(SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failedCount,
            COALESCE(SUM(CASE WHEN status IN ('PENDING', 'RETRYING', 'SENDING') THEN 1 ELSE 0 END), 0) AS pendingCount,
            COALESCE(SUM(CASE WHEN status = 'IGNORED' THEN 1 ELSE 0 END), 0) AS ignoredCount
        FROM forward_records
        """,
    )
    fun observeSummary(): Flow<ForwardSummary>
}
