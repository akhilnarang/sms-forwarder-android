package dev.akhilnarang.smsforwarder.network

import dev.akhilnarang.smsforwarder.data.ForwardRecordEntity

interface ForwardClientInterface {
    suspend fun forward(
        record: ForwardRecordEntity,
        endpointUrl: String,
        authHeaderName: String,
        authHeaderValue: String,
    ): ForwardResult
}
