package dev.akhilnarang.smsforwarder.network

import dev.akhilnarang.smsforwarder.data.ForwardRecordEntity
import dev.akhilnarang.smsforwarder.settings.AppSettings

interface ForwardClientInterface {
    suspend fun forward(record: ForwardRecordEntity, settings: AppSettings): ForwardResult
}
