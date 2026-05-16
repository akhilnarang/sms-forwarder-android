package dev.akhilnarang.smsforwarder.work

import dev.akhilnarang.smsforwarder.data.DeliveryStatus
import dev.akhilnarang.smsforwarder.data.DestinationEntity
import dev.akhilnarang.smsforwarder.data.DestinationRepository
import dev.akhilnarang.smsforwarder.data.DestinationDao
import dev.akhilnarang.smsforwarder.data.ForwardRecordEntity
import dev.akhilnarang.smsforwarder.data.ForwardRecordGateway
import dev.akhilnarang.smsforwarder.network.ForwardClientInterface
import dev.akhilnarang.smsforwarder.network.ForwardResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
            destinationId = 1L,
            payloadJson = "{}",
            status = DeliveryStatus.PENDING,
            statusReason = "test",
            responseDetails = null,
        )

    private val baseDestination = 
        DestinationEntity(
            id = 1L,
            label = "Test",
            type = dev.akhilnarang.smsforwarder.data.DestinationType.CUSTOM_WEBHOOK,
            endpointUrl = "http://example.com"
        )

    private fun makeExecutor(
        record: ForwardRecordEntity? = baseRecord,
        destination: DestinationEntity? = baseDestination,
        claimResult: Boolean = true,
        forwardResult: ForwardResult = ForwardResult.Success("OK"),
        onForwardCalled: () -> Unit = {},
        onForwardConfig: (String, String, String) -> Unit = { _, _, _ -> },
    ): Pair<ForwardWorkExecutor, RecordingGateway> {
        val gateway = RecordingGateway(record, claimResult)
        val client = object : ForwardClientInterface {
            override suspend fun forward(
                record: ForwardRecordEntity,
                endpointUrl: String,
                authHeaderName: String,
                authHeaderValue: String,
            ): ForwardResult {
                onForwardCalled()
                onForwardConfig(endpointUrl, authHeaderName, authHeaderValue)
                return forwardResult
            }
        }
        val destDao = object : DestinationDao {
            override fun getAll(): Flow<List<DestinationEntity>> = flowOf()
            override suspend fun getById(id: Long): DestinationEntity? = destination
            override fun getEnabled(): Flow<List<DestinationEntity>> =
                flowOf(listOfNotNull(destination).filter { it.enabled })
            override suspend fun getByIdIfEnabled(id: Long): DestinationEntity? =
                destination?.takeIf { it.id == id && it.enabled }
            override suspend fun insert(destination: DestinationEntity): Long = 1L
            override suspend fun update(destination: DestinationEntity) {}
            override suspend fun delete(destination: DestinationEntity) {}
            override suspend fun deleteAll() {}
        }
        val destRepo = DestinationRepository(destDao)
        return ForwardWorkExecutor(gateway, client, destRepo, null) to gateway
    }

    @Test
    fun `success path marks record sent`() = runTest {
        val (executor, gateway) = makeExecutor(forwardResult = ForwardResult.Success("OK"))
        assertEquals(ForwardWorkExecutor.WorkResult.SUCCESS, executor.execute(1L, 0))
        assertEquals(DeliveryStatus.SENT, gateway.lastStatusSet)
    }

    @Test
    fun `permanent failure marks record failed and returns FAILURE`() = runTest {
        val (executor, gateway) = makeExecutor(forwardResult = ForwardResult.PermanentFailure("403"))
        assertEquals(ForwardWorkExecutor.WorkResult.FAILURE, executor.execute(1L, 0))
        assertEquals(DeliveryStatus.FAILED, gateway.lastStatusSet)
    }

    @Test
    fun `retryable failure below limit returns RETRY and marks RETRYING`() = runTest {
        val (executor, gateway) = makeExecutor(
            forwardResult = ForwardResult.RetryableFailure("timeout")
        )
        assertEquals(ForwardWorkExecutor.WorkResult.RETRY, executor.execute(1L, 0))
        assertEquals(DeliveryStatus.RETRYING, gateway.lastStatusSet)
    }

    @Test
    fun `retryable failure at attempt limit marks FAILED and returns FAILURE`() = runTest {
        val (executor, gateway) = makeExecutor(
            forwardResult = ForwardResult.RetryableFailure("timeout")
        )
        assertEquals(ForwardWorkExecutor.WorkResult.FAILURE, executor.execute(1L, 4))
        assertEquals(DeliveryStatus.FAILED, gateway.lastStatusSet)
    }

    @Test
    fun `unclaimed record returns FAILURE without forwarding`() = runTest {
        val (executor, gateway) = makeExecutor(claimResult = false)
        assertEquals(ForwardWorkExecutor.WorkResult.FAILURE, executor.execute(1L, 0))
        assertNull(gateway.lastStatusSet)
    }

    @Test
    fun `missing record returns FAILURE`() = runTest {
        val (executor, _) = makeExecutor(record = null, claimResult = true)
        assertEquals(ForwardWorkExecutor.WorkResult.FAILURE, executor.execute(1L, 0))
    }

    @Test
    fun `record with null destinationId is marked FAILED instead of routed to fallback`() = runTest {
        var forwardCalls = 0
        val (executor, gateway) = makeExecutor(
            record = baseRecord.copy(destinationId = null),
            onForwardCalled = { forwardCalls++ },
        )

        assertEquals(ForwardWorkExecutor.WorkResult.FAILURE, executor.execute(1L, 0))
        assertEquals(DeliveryStatus.FAILED, gateway.lastStatusSet)
        assertTrue(gateway.lastError?.contains("destination", ignoreCase = true) == true)
        assertEquals(0, forwardCalls)
    }

    @Test
    fun `forwards using destination endpoint and auth header`() = runTest {
        var capturedEndpoint: String? = null
        var capturedHeaderName: String? = null
        var capturedHeaderValue: String? = null
        val destination =
            baseDestination.copy(
                endpointUrl = "https://example.com/hook",
                authHeaderName = "Authorization",
                authHeaderValue = "Bearer token",
            )
        val (executor, _) = makeExecutor(
            destination = destination,
            onForwardConfig = { endpointUrl, authHeaderName, authHeaderValue ->
                capturedEndpoint = endpointUrl
                capturedHeaderName = authHeaderName
                capturedHeaderValue = authHeaderValue
            },
        )

        assertEquals(ForwardWorkExecutor.WorkResult.SUCCESS, executor.execute(1L, 0))
        assertEquals("https://example.com/hook", capturedEndpoint)
        assertEquals("Authorization", capturedHeaderName)
        assertEquals("Bearer token", capturedHeaderValue)
    }
}

private class RecordingGateway(
    private val record: ForwardRecordEntity?,
    private val claimResult: Boolean,
) : ForwardRecordGateway {
    var lastStatusSet: DeliveryStatus? = null
    var lastError: String? = null

    override suspend fun markSendingIfEligible(id: Long, attemptedAtEpochMs: Long): Boolean = claimResult

    override suspend fun getById(id: Long): ForwardRecordEntity? = record

    override suspend fun markSent(id: Long, sentAtEpochMs: Long, responseDetails: String?) {
        lastStatusSet = DeliveryStatus.SENT
    }

    override suspend fun markFailed(id: Long, error: String) {
        lastStatusSet = DeliveryStatus.FAILED
        lastError = error
    }

    override suspend fun markRetrying(id: Long, error: String) {
        lastStatusSet = DeliveryStatus.RETRYING
        lastError = error
    }

    override suspend fun countByStatus(status: DeliveryStatus): Int = 0
}
