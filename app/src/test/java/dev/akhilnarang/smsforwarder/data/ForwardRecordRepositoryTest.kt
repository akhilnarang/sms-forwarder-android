package dev.akhilnarang.smsforwarder.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForwardRecordRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ForwardRecordRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repo = ForwardRecordRepository(db.forwardRecordDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertPending(): Long =
        repo.insertTestRecord(
            senderTag = "[TEST]",
            messageBody = "test body",
            receivedAtEpochMs = 1_000_000L,
            payloadJson = "{}",
        )

    /**
     * Claims a record using a stale-SENDING cutoff older than the attempt timestamp,
     * matching how [ForwardWorkExecutor] derives it. With this cutoff a freshly-claimed
     * SENDING row is never treated as stale.
     */
    private suspend fun claim(id: Long, attemptedAtEpochMs: Long): Boolean =
        repo.markSendingIfEligible(
            id = id,
            attemptedAtEpochMs = attemptedAtEpochMs,
            staleSendingBeforeEpochMs = attemptedAtEpochMs - STALE_CUTOFF_SLACK_MS,
        )

    @Test
    fun `markSendingIfEligible returns true for PENDING record`() = runTest {
        val id = insertPending()
        assertTrue(claim(id, System.currentTimeMillis()))
    }

    @Test
    fun `markSendingIfEligible returns false for fresh SENDING record`() = runTest {
        val id = insertPending()
        val now = System.currentTimeMillis()
        claim(id, now)
        // Second claim shortly after: the SENDING row is not yet stale, so not reclaimable.
        assertFalse(claim(id, now + 1))
    }

    @Test
    fun `markSendingIfEligible reclaims a stale SENDING record`() = runTest {
        val id = insertPending()
        claim(id, 1000L)
        // Cutoff in the future relative to the row's lastAttemptedAtEpochMs (1000L)
        // marks the SENDING row as stale, so it becomes reclaimable.
        assertTrue(
            repo.markSendingIfEligible(
                id = id,
                attemptedAtEpochMs = 1_000_000L,
                staleSendingBeforeEpochMs = 500_000L,
            ),
        )
    }

    @Test
    fun `markSent transitions record to SENT status`() = runTest {
        val id = insertPending()
        claim(id, 1000L)
        repo.markSent(id, 2000L, "OK")
        val record = repo.getById(id)!!
        assertEquals(DeliveryStatus.SENT, record.status)
        assertEquals(2000L, record.sentAtEpochMs)
    }

    @Test
    fun `markFailed transitions record to FAILED with error`() = runTest {
        val id = insertPending()
        claim(id, 1000L)
        repo.markFailed(id, "connection refused")
        val record = repo.getById(id)!!
        assertEquals(DeliveryStatus.FAILED, record.status)
        assertEquals("connection refused", record.responseDetails)
    }

    @Test
    fun `markRetrying transitions record to RETRYING with error`() = runTest {
        val id = insertPending()
        claim(id, 1000L)
        repo.markRetrying(id, "timeout")
        val record = repo.getById(id)!!
        assertEquals(DeliveryStatus.RETRYING, record.status)
        assertEquals("timeout", record.responseDetails)
    }

    @Test
    fun `markPending resets a FAILED record back to PENDING`() = runTest {
        val id = insertPending()
        claim(id, 1000L)
        repo.markFailed(id, "network error")
        repo.markPending(id)
        val record = repo.getById(id)!!
        assertEquals(DeliveryStatus.PENDING, record.status)
        assertNull(record.responseDetails)
    }

    @Test
    fun `markSendingIfEligible returns true for FAILED record`() = runTest {
        val id = insertPending()
        claim(id, 1000L)
        repo.markFailed(id, "error")
        assertTrue(claim(id, 2000L))
    }

    @Test
    fun `markSendingIfEligible increments attemptCount`() = runTest {
        val id = insertPending()
        claim(id, 1000L)
        val record = repo.getById(id)!!
        assertEquals(1, record.attemptCount)
    }

    @Test
    fun `observeAll returns inserted records`() = runTest {
        val id = insertPending()
        val records = repo.observeAll().first()
        assertTrue(records.any { it.id == id })
    }

    @Test
    fun `insertTestRecord sets isTestRecord true`() = runTest {
        val id = insertPending()
        val record = repo.getById(id)!!
        assertTrue(record.isTestRecord)
    }

    private companion object {
        const val STALE_CUTOFF_SLACK_MS = 60_000L
    }
}
