package dev.akhilnarang.smsforwarder.work

import dev.akhilnarang.smsforwarder.data.DeliveryStatus
import dev.akhilnarang.smsforwarder.data.ForwardRecordEntity
import dev.akhilnarang.smsforwarder.data.ForwardRecordGateway
import dev.akhilnarang.smsforwarder.network.ForwardClientInterface
import dev.akhilnarang.smsforwarder.network.ForwardResult
import dev.akhilnarang.smsforwarder.settings.AppSettings
import dev.akhilnarang.smsforwarder.settings.SettingsGateway
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForwardWorkExecutorTest {
    private val baseRecord =
        ForwardRecordEntity(
            id = 1L,
            senderRaw = "[TEST]",
            senderNormalized = "[TEST]",
            messageBody = "test",
            receivedAtEpochMs = 1000L,
            subscriptionId = null,
            multipart = false,
            payloadJson = "{}",
            status = DeliveryStatus.PENDING,
            statusReason = "test",
            lastError = null,
        )

    private fun makeExecutor(
        record: ForwardRecordEntity? = baseRecord,
        claimResult: Boolean = true,
        forwardResult: ForwardResult = ForwardResult.Success,
        maxRetries: Int = ForwardWorkExecutor.MAX_AUTOMATIC_RETRY_ATTEMPTS,
    ): Pair<ForwardWorkExecutor, RecordingGateway> {
        val gateway = RecordingGateway(record, claimResult)
        val client = object : ForwardClientInterface {
            override suspend fun forward(record: ForwardRecordEntity, settings: AppSettings): ForwardResult =
                forwardResult
        }
        val settings = object : SettingsGateway {
            override fun currentSettings(): AppSettings = AppSettings()
        }
        return ForwardWorkExecutor(gateway, settings, client, maxRetries) to gateway
    }

    @Test
    fun `success path marks record sent`() = runTest {
        val (executor, gateway) = makeExecutor(forwardResult = ForwardResult.Success)
        assertEquals(ForwardWorkExecutor.WorkResult.SUCCESS, executor.execute(1L, 0))
        assertEquals(DeliveryStatus.SENT, gateway.lastStatusSet)
    }

    @Test
    fun `permanent failure marks record failed and returns SUCCESS`() = runTest {
        val (executor, gateway) = makeExecutor(forwardResult = ForwardResult.PermanentFailure("403"))
        assertEquals(ForwardWorkExecutor.WorkResult.SUCCESS, executor.execute(1L, 0))
        assertEquals(DeliveryStatus.FAILED, gateway.lastStatusSet)
    }

    @Test
    fun `retryable failure below limit returns RETRY and marks RETRYING`() = runTest {
        val (executor, gateway) = makeExecutor(
            forwardResult = ForwardResult.RetryableFailure("timeout"),
            maxRetries = 5,
        )
        assertEquals(ForwardWorkExecutor.WorkResult.RETRY, executor.execute(1L, 0))
        assertEquals(DeliveryStatus.RETRYING, gateway.lastStatusSet)
    }

    @Test
    fun `retryable failure at attempt limit marks FAILED and returns SUCCESS`() = runTest {
        val (executor, gateway) = makeExecutor(
            forwardResult = ForwardResult.RetryableFailure("timeout"),
            maxRetries = 5,
        )
        assertEquals(ForwardWorkExecutor.WorkResult.SUCCESS, executor.execute(1L, 4))
        assertEquals(DeliveryStatus.FAILED, gateway.lastStatusSet)
    }

    @Test
    fun `unclaimed record returns SUCCESS without forwarding`() = runTest {
        val (executor, gateway) = makeExecutor(claimResult = false)
        assertEquals(ForwardWorkExecutor.WorkResult.SUCCESS, executor.execute(1L, 0))
        assertNull(gateway.lastStatusSet)
    }

    @Test
    fun `missing record returns FAILURE`() = runTest {
        val (executor, _) = makeExecutor(record = null, claimResult = true)
        assertEquals(ForwardWorkExecutor.WorkResult.FAILURE, executor.execute(1L, 0))
    }
}

private class RecordingGateway(
    private val record: ForwardRecordEntity?,
    private val claimResult: Boolean,
) : ForwardRecordGateway {
    var lastStatusSet: DeliveryStatus? = null

    override suspend fun markSendingIfEligible(id: Long, attemptedAtEpochMs: Long): Boolean = claimResult

    override suspend fun getById(id: Long): ForwardRecordEntity? = record

    override suspend fun markSent(id: Long, sentAtEpochMs: Long) {
        lastStatusSet = DeliveryStatus.SENT
    }

    override suspend fun markFailed(id: Long, error: String) {
        lastStatusSet = DeliveryStatus.FAILED
    }

    override suspend fun markRetrying(id: Long, error: String) {
        lastStatusSet = DeliveryStatus.RETRYING
    }
}
