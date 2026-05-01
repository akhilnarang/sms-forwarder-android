package dev.akhilnarang.smsforwarder.data

interface ForwardRecordGateway {
    suspend fun markSendingIfEligible(id: Long, attemptedAtEpochMs: Long): Boolean
    suspend fun getById(id: Long): ForwardRecordEntity?
    suspend fun markSent(id: Long, sentAtEpochMs: Long, responseDetails: String?)
    suspend fun markFailed(id: Long, error: String)
    suspend fun markRetrying(id: Long, error: String)
}
