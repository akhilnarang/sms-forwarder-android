package dev.akhilnarang.smsforwarder

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SmsForwarderApp : Application() {
    // Defense-in-depth: any uncaught throwable in an applicationScope coroutine is
    // logged (metadata only, never SMS body/sender) instead of crashing the process.
    private val coroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Uncaught exception in applicationScope.", throwable)
        }

    val applicationScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)
    val container by lazy { AppContainer(this) }

    private companion object {
        const val TAG = "SmsForwarderApp"
    }
}
