package dev.akhilnarang.smsforwarder.sms

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.akhilnarang.smsforwarder.data.DeliveryStatus
import dev.akhilnarang.smsforwarder.data.DestinationDao
import dev.akhilnarang.smsforwarder.data.DestinationEntity
import dev.akhilnarang.smsforwarder.data.DestinationType
import dev.akhilnarang.smsforwarder.data.ForwardRecordDao
import dev.akhilnarang.smsforwarder.data.ForwardRecordEntity
import dev.akhilnarang.smsforwarder.data.ForwardRecordRepository
import dev.akhilnarang.smsforwarder.data.ForwardSummary
import dev.akhilnarang.smsforwarder.data.ForwardingRuleDao
import dev.akhilnarang.smsforwarder.data.ForwardingRuleEntity
import dev.akhilnarang.smsforwarder.network.ForwardPayloadFactory
import dev.akhilnarang.smsforwarder.work.ForwardWorkScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmsProcessorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val recordingScheduler = RecordingForwardWorkScheduler(context)

    @Test
    fun `telegram payload build failure marks record FAILED with reason and does not enqueue`() = runTest {
        val rule =
            ForwardingRuleEntity(
                id = 7L,
                priority = 0,
                label = "Telegram",
                senderPattern = "*",
                destinationId = 42L,
                enabled = true,
            )
        val destination =
            DestinationEntity(
                id = 42L,
                label = "Telegram",
                type = DestinationType.TELEGRAM_PRESET,
                endpointUrl = "https://api.telegram.org/botTOKEN/sendMessage",
                configJson = "{",
            )
        val recordDao = RecordingForwardRecordDao()
        val processor =
            SmsProcessor(
                ruleDao = FakeForwardingRuleDao(listOf(rule)),
                destinationDao = FakeDestinationDao(destination),
                forwardRecordRepository = ForwardRecordRepository(recordDao),
                payloadFactory = ForwardPayloadFactory(),
                workScheduler = recordingScheduler,
            )

        processor.handleIncomingSms(
            IncomingSms(
                senderRaw = "+15551234567",
                senderNormalized = "+15551234567",
                body = "hello",
                receivedAtEpochMs = 1_000L,
                subscriptionId = null,
                multipart = false,
            ),
        )

        val record = recordDao.getById(100L)!!
        assertEquals(1, recordDao.insertedCount)
        assertEquals("", record.payloadJson)
        assertEquals(DeliveryStatus.FAILED, record.status)
        assertTrue(record.responseDetails!!.contains("Payload build failed"))
        assertTrue("scheduler must not be invoked when payload build fails", recordingScheduler.enqueued.isEmpty())
    }

    @Test
    fun `rule pointing at disabled destination causes FAILED record without enqueue`() = runTest {
        val rule =
            ForwardingRuleEntity(
                id = 8L,
                priority = 0,
                label = "Disabled destination",
                senderPattern = "*BANK",
                bodyContains = "*OTP*",
                destinationId = 1L,
                enabled = true,
            )
        val destination =
            DestinationEntity(
                id = 1L,
                label = "Disabled",
                type = DestinationType.CUSTOM_WEBHOOK,
                endpointUrl = "https://example.com/disabled",
                enabled = false,
            )
        val recordDao = RecordingForwardRecordDao()
        val scheduler = RecordingForwardWorkScheduler(context)
        val processor =
            SmsProcessor(
                ruleDao = FakeForwardingRuleDao(listOf(rule)),
                destinationDao = FakeDestinationDao(destination),
                forwardRecordRepository = ForwardRecordRepository(recordDao),
                payloadFactory = ForwardPayloadFactory(),
                workScheduler = scheduler,
            )

        processor.handleIncomingSms(
            IncomingSms(
                senderRaw = "MYBANK",
                senderNormalized = "MYBANK",
                body = "Your OTP is 123456",
                receivedAtEpochMs = 1_000L,
                subscriptionId = null,
                multipart = false,
            ),
        )

        val record = recordDao.getById(100L)!!
        assertEquals(1, recordDao.insertedCount)
        assertEquals(DeliveryStatus.FAILED, record.status)
        assertTrue(record.responseDetails!!.contains("destination", ignoreCase = true))
        assertTrue("scheduler must not be invoked for disabled destination", scheduler.enqueued.isEmpty())
    }
}

private class RecordingForwardWorkScheduler(context: Context) : ForwardWorkScheduler(context) {
    val enqueued = mutableListOf<Long>()
    override fun enqueue(recordId: Long) { enqueued += recordId }
    override fun retryNow(recordId: Long) { enqueued += recordId }
}

private class FakeForwardingRuleDao(
    private val enabledRules: List<ForwardingRuleEntity>,
) : ForwardingRuleDao {
    override fun getAll(): Flow<List<ForwardingRuleEntity>> = flowOf(enabledRules)

    override suspend fun getEnabledRules(): List<ForwardingRuleEntity> = enabledRules

    override suspend fun getMaxPriority(): Int? = enabledRules.maxOfOrNull { it.priority }

    override suspend fun insert(rule: ForwardingRuleEntity): Long = rule.id

    override suspend fun update(rule: ForwardingRuleEntity) = Unit

    override suspend fun delete(rule: ForwardingRuleEntity) = Unit
}

private class FakeDestinationDao(
    private val destination: DestinationEntity,
) : DestinationDao {
    override fun getAll(): Flow<List<DestinationEntity>> = flowOf(listOf(destination))

    override suspend fun getById(id: Long): DestinationEntity? =
        destination.takeIf { it.id == id }

    override fun getEnabled(): Flow<List<DestinationEntity>> =
        flowOf(listOf(destination).filter { it.enabled })

    override suspend fun getByIdIfEnabled(id: Long): DestinationEntity? =
        destination.takeIf { it.id == id && it.enabled }

    override suspend fun insert(destination: DestinationEntity): Long = destination.id

    override suspend fun update(destination: DestinationEntity) = Unit

    override suspend fun delete(destination: DestinationEntity) = Unit
}

private class RecordingForwardRecordDao : ForwardRecordDao {
    private val records = mutableMapOf<Long, ForwardRecordEntity>()
    var insertedCount = 0
        private set

    override suspend fun insert(entity: ForwardRecordEntity): Long {
        val id = 100L
        insertedCount++
        records[id] = entity.copy(id = id)
        return id
    }

    override fun observeAll(): Flow<List<ForwardRecordEntity>> = flowOf(records.values.toList())

    override suspend fun getById(id: Long): ForwardRecordEntity? = records[id]

    override suspend fun updateStatus(id: Long, status: DeliveryStatus) {
        records[id] = records.getValue(id).copy(status = status, responseDetails = null)
    }

    override suspend fun markSendingIfEligible(
        id: Long,
        status: DeliveryStatus,
        attemptedAtEpochMs: Long,
    ): Int = 0

    override suspend fun updateStatusWithError(
        id: Long,
        error: String,
        status: DeliveryStatus,
    ) {
        records[id] = records.getValue(id).copy(status = status, responseDetails = error)
    }

    override suspend fun markSent(
        id: Long,
        sentAtEpochMs: Long,
        responseDetails: String?,
        status: DeliveryStatus,
    ) {
        records[id] =
            records.getValue(id).copy(
                status = status,
                sentAtEpochMs = sentAtEpochMs,
                responseDetails = responseDetails,
            )
    }

    override suspend fun deleteAll() {
        records.clear()
    }

    override fun observeSummary(): Flow<ForwardSummary> = flowOf(ForwardSummary())
}
