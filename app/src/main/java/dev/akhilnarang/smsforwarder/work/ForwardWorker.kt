package dev.akhilnarang.smsforwarder.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.akhilnarang.smsforwarder.SmsForwarderApp

class ForwardWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val recordId = inputData.getLong(KEY_RECORD_ID, INVALID_RECORD_ID)
        if (recordId == INVALID_RECORD_ID) {
            return Result.failure()
        }

        val container = (applicationContext as SmsForwarderApp).container
        val executor = ForwardWorkExecutor(
            recordGateway = container.forwardRecordRepository,
            settingsGateway = container.settingsRepository,
            forwardClient = container.forwardClient,
        )

        return when (executor.execute(recordId, runAttemptCount)) {
            ForwardWorkExecutor.WorkResult.SUCCESS -> Result.success()
            ForwardWorkExecutor.WorkResult.FAILURE -> Result.failure()
            ForwardWorkExecutor.WorkResult.RETRY -> Result.retry()
        }
    }

    companion object {
        const val KEY_RECORD_ID = "record_id"
        private const val INVALID_RECORD_ID = -1L
    }
}
