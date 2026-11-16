package dev.akhilnarang.smsforwarder.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

open class ForwardWorkScheduler(
    private val context: Context,
) {
    private val workManager by lazy { WorkManager.getInstance(context) }

    open fun enqueue(recordId: Long) {
        enqueue(recordId, ExistingWorkPolicy.KEEP)
    }

    open fun retryNow(recordId: Long) {
        enqueue(recordId, ExistingWorkPolicy.REPLACE)
    }

    private fun enqueue(
        recordId: Long,
        existingWorkPolicy: ExistingWorkPolicy,
    ) {
        val request =
            OneTimeWorkRequestBuilder<ForwardWorker>()
                .setInputData(workDataOf(ForwardWorker.KEY_RECORD_ID to recordId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_DELAY_MS,
                    TimeUnit.MILLISECONDS,
                )
                .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(recordId),
            existingWorkPolicy,
            request,
        )
    }

    private fun uniqueWorkName(recordId: Long): String = "forward-record-$recordId"

    private companion object {
        // Must stay at or above the stale-SENDING cutoff so a RETRY for a fresh SENDING
        // row lands after the cutoff and the next run can reclaim it.
        const val BACKOFF_DELAY_MS = ForwardWorkExecutor.STALE_SENDING_THRESHOLD_MS
    }
}
